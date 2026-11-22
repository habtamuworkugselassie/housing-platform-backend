package com.housingplatform.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.repository.OutreachContactRepository;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import com.housingplatform.reservation.service.LeadNudgeService;
import com.housingplatform.reservation.service.ReservationMailService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LeadNudgeServiceTest {

  private final ExhibitionInterestRepository leads = mock(ExhibitionInterestRepository.class);
  private final SponsorshipReservationRepository reservations =
      mock(SponsorshipReservationRepository.class);
  private final OutreachContactRepository outreach = mock(OutreachContactRepository.class);
  private final ReservationMailService mail = mock(ReservationMailService.class);
  private final LeadNudgeService service =
      new LeadNudgeService(leads, reservations, outreach, mail);

  // Wednesday 2026-11-25 10:00 Addis.
  private final LocalDateTime now = LocalDateTime.of(2026, 11, 25, 10, 0);

  LeadNudgeServiceTest() {
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "firstAfterDays", 3);
    ReflectionTestUtils.setField(service, "againAfterDays", 7);
    ReflectionTestUtils.setField(service, "max", 2);
    when(leads.save(any())).thenAnswer(i -> i.getArgument(0));
    when(reservations.findByEmailIgnoreCaseOrderByCreatedAtDesc(anyString())).thenReturn(List.of());
    when(outreach.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
  }

  private ExhibitionInterest lead(String email, LocalDateTime created) {
    ExhibitionInterest e =
        ExhibitionInterest.builder()
            .email(email)
            .interestType("exhibitor")
            .sponsorship(Sponsorship.builder().name("Gold").build())
            .locale("en")
            .build();
    e.setCreatedAt(created);
    return e;
  }

  @Test
  void nudgesALeadThreeBusinessDaysOldAndNotOneFromYesterday() {
    ExhibitionInterest old =
        lead("old@example.com", now.minusDays(6)); // Thu 19 Nov: 4 business days
    ExhibitionInterest fresh = lead("fresh@example.com", now.minusDays(1));
    when(leads.findNudgeCandidates(anyInt())).thenReturn(List.of(old, fresh));

    assertThat(service.run(now)).isEqualTo(1);
    verify(mail, times(1)).sendReserveInvite(old);
    verify(mail, never()).sendReserveInvite(fresh);
    assertThat(old.getNudgeCount()).isEqualTo(1);
    assertThat(old.getReserveInvitedAt()).isEqualTo(now);
  }

  @Test
  void waitsAWeekAfterAnyInvitationAndSkipsOptOutsAndReservedLeads() {
    ExhibitionInterest invitedRecently = lead("recent@example.com", now.minusDays(20));
    invitedRecently.setReserveInvitedAt(now.minusDays(3));
    ExhibitionInterest invitedLongAgo = lead("ago@example.com", now.minusDays(30));
    invitedLongAgo.setReserveInvitedAt(now.minusDays(12));
    ExhibitionInterest opted = lead("opted@example.com", now.minusDays(30));
    ExhibitionInterest reserved = lead("reserved@example.com", now.minusDays(30));
    when(leads.findNudgeCandidates(anyInt()))
        .thenReturn(List.of(invitedRecently, invitedLongAgo, opted, reserved));
    OutreachContact c = new OutreachContact();
    c.setStatus(OutreachContact.Status.OPTED_OUT);
    when(outreach.findByEmailIgnoreCase("opted@example.com")).thenReturn(Optional.of(c));
    SponsorshipReservation r =
        SponsorshipReservation.builder().status(SponsorshipReservation.Status.REQUESTED).build();
    when(reservations.findByEmailIgnoreCaseOrderByCreatedAtDesc("reserved@example.com"))
        .thenReturn(List.of(r));

    assertThat(service.run(now)).isEqualTo(1);
    verify(mail).sendReserveInvite(invitedLongAgo);
    verify(mail, never()).sendReserveInvite(invitedRecently);
    verify(mail, never()).sendReserveInvite(opted);
    verify(mail, never()).sendReserveInvite(reserved);
  }
}
