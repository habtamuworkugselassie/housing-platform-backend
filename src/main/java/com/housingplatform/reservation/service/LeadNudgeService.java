package com.housingplatform.reservation.service;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.repository.OutreachContactRepository;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns leads into reservations: an exhibitor or partner lead that chose a package and never
 * reserved gets the "Reserve your place" email after a few business days, once more a week later,
 * then nothing. A reservation on the lead's organization or email, an outreach opt-out or bounce,
 * or a manual invitation in the meantime all count.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadNudgeService {

  private final ExhibitionInterestRepository leads;
  private final SponsorshipReservationRepository reservations;
  private final OutreachContactRepository outreach;
  private final ReservationMailService mail;

  @Value("${app.lead-nudge.enabled:true}")
  private boolean enabled;

  @Value("${app.lead-nudge.first-after-days:3}")
  private int firstAfterDays;

  @Value("${app.lead-nudge.again-after-days:7}")
  private int againAfterDays;

  @Value("${app.lead-nudge.max:2}")
  private int max;

  @Scheduled(cron = "0 0 10 * * MON-FRI", zone = "Africa/Addis_Ababa")
  public void tick() {
    if (!enabled) return;
    int sent = run(LocalDateTime.now());
    if (sent > 0) log.info("Lead nudges sent: {}", sent);
  }

  /** Sends every nudge due at {@code now}; returns how many went out. */
  @Transactional
  public int run(LocalDateTime now) {
    int sent = 0;
    for (ExhibitionInterest lead : leads.findNudgeCandidates(max)) {
      if (!due(lead, now) || hasReservation(lead) || optedOut(lead)) continue;
      lead.setNudgeCount(lead.getNudgeCount() + 1);
      lead.setReserveInvitedAt(now);
      ExhibitionInterest saved = leads.save(lead);
      mail.sendReserveInvite(saved);
      sent++;
    }
    return sent;
  }

  boolean due(ExhibitionInterest lead, LocalDateTime now) {
    if (lead.getReserveInvitedAt() == null) {
      return lead.getCreatedAt() != null
          && !lead.getCreatedAt()
              .isAfter(ReservationService.minusBusinessDays(now, firstAfterDays));
    }
    return !lead.getReserveInvitedAt()
        .isAfter(ReservationService.minusBusinessDays(now, againAfterDays));
  }

  private boolean hasReservation(ExhibitionInterest lead) {
    List<SponsorshipReservation> list =
        lead.getOrganization() != null
            ? reservations.findByOrganizationIdOrderByCreatedAtDesc(lead.getOrganization().getId())
            : List.of();
    if (list.isEmpty())
      list = reservations.findByEmailIgnoreCaseOrderByCreatedAtDesc(lead.getEmail());
    return list.stream().anyMatch(r -> r.getStatus().open());
  }

  private boolean optedOut(ExhibitionInterest lead) {
    return outreach
        .findByEmailIgnoreCase(lead.getEmail())
        .map(
            c ->
                c.getStatus() == OutreachContact.Status.OPTED_OUT
                    || c.getStatus() == OutreachContact.Status.BOUNCED)
        .orElse(false);
  }
}
