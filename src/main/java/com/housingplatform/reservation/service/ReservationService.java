package com.housingplatform.reservation.service;

import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.repository.SponsorshipRepository;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.domain.SponsorshipReservation.Status;
import com.housingplatform.reservation.dto.ReservationDtos.AdminRow;
import com.housingplatform.reservation.dto.ReservationDtos.AdminUpdate;
import com.housingplatform.reservation.dto.ReservationDtos.BankAccount;
import com.housingplatform.reservation.dto.ReservationDtos.Confirmation;
import com.housingplatform.reservation.dto.ReservationDtos.Pipeline;
import com.housingplatform.reservation.dto.ReservationDtos.ReserveRequest;
import com.housingplatform.reservation.dto.ReservationDtos.StatusTotal;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.mail.MailLocale;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SponsorshipReservationRepository repository;
  private final SponsorshipRepository sponsorships;
  private final ReservationMailService mail;

  @Transactional
  public Confirmation reserve(ReserveRequest r) {
    if (r.getWebsite() != null && !r.getWebsite().isBlank()) {
      log.info("Reservation honeypot tripped for {}", r.getEmail());
      return Confirmation.builder().reference("RSV-XXXXXX").status("REQUESTED").build();
    }
    if (r.getSponsorshipId() == null) throw new BusinessException("Please choose a package");
    Sponsorship pkg =
        sponsorships
            .findById(r.getSponsorshipId())
            .filter(s -> s.getStatus() == Sponsorship.SponsorshipStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException("That package is no longer available"));

    SponsorshipReservation saved =
        repository.save(
            SponsorshipReservation.builder()
                .reference(newReference())
                .sponsorship(pkg)
                .packageName(pkg.getName())
                .packageType(pkg.getType() != null ? pkg.getType().name() : null)
                .amount(pkg.getBasePrice() == null ? BigDecimal.ZERO : pkg.getBasePrice())
                .currency("ETB")
                .company(r.getCompany().trim())
                .contactName(r.getContactName().trim())
                .email(r.getEmail().trim())
                .phone(blankToNull(r.getPhone()))
                .tin(blankToNull(r.getTin()))
                .boothPreference(blankToNull(r.getBoothPreference()))
                .message(blankToNull(r.getMessage()))
                .locale(MailLocale.normalize(r.getLocale()))
                .status(Status.REQUESTED)
                .amountPaid(BigDecimal.ZERO)
                .source(blankToNull(r.getSource()) == null ? "web" : r.getSource().trim())
                .reminderCount(0)
                .statusChangedAt(LocalDateTime.now())
                .build());
    afterCommit(() -> mail.sendConfirmation(saved, true));
    return toConfirmation(saved);
  }

  // ---------------------------------------------------------------- admin

  @Transactional(readOnly = true)
  public Page<AdminRow> search(String q, Status status, Pageable pageable) {
    Specification<SponsorshipReservation> spec = (root, query, cb) -> cb.conjunction();
    if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    if (q != null && !q.isBlank()) {
      String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("company")), like),
                      cb.like(cb.lower(root.get("contactName")), like),
                      cb.like(cb.lower(root.get("email")), like),
                      cb.like(cb.lower(root.get("reference")), like),
                      cb.like(cb.lower(root.get("packageName")), like)));
    }
    Pageable sorted =
        pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    return repository.findAll(spec, sorted).map(ReservationService::toRow);
  }

  @Transactional(readOnly = true)
  public Pipeline pipeline() {
    Map<String, StatusTotal> by = new LinkedHashMap<>();
    for (Status s : Status.values())
      by.put(s.name(), new StatusTotal(0, BigDecimal.ZERO, BigDecimal.ZERO));
    long openCount = 0;
    BigDecimal openValue = BigDecimal.ZERO;
    BigDecimal paidValue = BigDecimal.ZERO;
    for (Object[] row : repository.totalsByStatus()) {
      Status s = (Status) row[0];
      long count = ((Number) row[1]).longValue();
      BigDecimal value = (BigDecimal) row[2];
      BigDecimal paid = (BigDecimal) row[3];
      by.put(s.name(), new StatusTotal(count, value, paid));
      if (s.open()) {
        openCount += count;
        openValue = openValue.add(value);
        paidValue = paidValue.add(paid);
      }
    }
    return Pipeline.builder()
        .openCount(openCount)
        .openValue(openValue)
        .paidValue(paidValue)
        .byStatus(by)
        .build();
  }

  @Transactional
  public AdminRow update(UUID id, AdminUpdate u) {
    SponsorshipReservation r = load(id);
    LocalDateTime now = LocalDateTime.now();
    if (u.getStatus() != null && !u.getStatus().isBlank()) {
      Status next = Status.valueOf(u.getStatus().trim().toUpperCase(Locale.ROOT));
      if (next != r.getStatus()) {
        r.setStatus(next);
        r.setStatusChangedAt(now);
        if (next == Status.AGREEMENT_SENT && r.getAgreementSentAt() == null)
          r.setAgreementSentAt(now);
        if (next == Status.SIGNED && r.getSignedAt() == null) r.setSignedAt(now);
        if (next == Status.PAID) {
          r.setPaidAt(now);
          if (r.getAmountPaid() == null || r.getAmountPaid().signum() == 0)
            r.setAmountPaid(r.getAmount());
        }
      }
    }
    if (u.getAmountPaid() != null) {
      if (u.getAmountPaid().signum() < 0)
        throw new BusinessException("Amount paid cannot be negative");
      r.setAmountPaid(u.getAmountPaid());
      if (r.getAmountPaid().compareTo(r.getAmount()) >= 0
          && r.getAmount().signum() > 0
          && r.getStatus().open()) {
        r.setStatus(Status.PAID);
        r.setStatusChangedAt(now);
        if (r.getPaidAt() == null) r.setPaidAt(now);
      } else if (r.getAmountPaid().signum() > 0
          && (r.getStatus() == Status.REQUESTED
              || r.getStatus() == Status.AGREEMENT_SENT
              || r.getStatus() == Status.SIGNED)) {
        r.setStatus(Status.DEPOSIT_PAID);
        r.setStatusChangedAt(now);
      }
    }
    if (u.getAmount() != null && u.getAmount().signum() >= 0) r.setAmount(u.getAmount());
    if (u.getAdminNotes() != null) r.setAdminNotes(blankToNull(u.getAdminNotes()));
    if (u.getAssignedTo() != null) r.setAssignedTo(blankToNull(u.getAssignedTo()));
    if (u.getBoothPreference() != null) r.setBoothPreference(blankToNull(u.getBoothPreference()));
    return toRow(repository.save(r));
  }

  /** Resend the confirmation with the agreement; moves REQUESTED to AGREEMENT_SENT. */
  @Transactional(readOnly = true)
  public void sendAgreement(UUID id) {
    mail.sendConfirmation(load(id), true);
  }

  @Transactional
  public AdminRow remind(UUID id) {
    SponsorshipReservation r = load(id);
    if (!r.getStatus().open() || r.getStatus() == Status.PAID) {
      throw new BusinessException("Nothing to remind: the reservation is " + r.getStatus());
    }
    r.setLastReminderAt(LocalDateTime.now());
    r.setReminderCount(r.getReminderCount() + 1);
    SponsorshipReservation saved = repository.save(r);
    afterCommit(() -> mail.sendReminder(saved));
    return toRow(saved);
  }

  /**
   * Reservations that have not moved for {@code afterBusinessDays} business days get one reminder,
   * at most {@code maxReminders} in total. Returns how many were sent.
   */
  @Transactional
  public int sendDueReminders(int afterBusinessDays, int maxReminders) {
    LocalDateTime cutoff = minusBusinessDays(LocalDateTime.now(), afterBusinessDays);
    int sent = 0;
    for (SponsorshipReservation r :
        repository.findByStatusInAndStatusChangedAtBefore(
            List.of(Status.REQUESTED, Status.AGREEMENT_SENT, Status.SIGNED), cutoff)) {
      if (r.getReminderCount() >= maxReminders) continue;
      if (r.getLastReminderAt() != null && r.getLastReminderAt().isAfter(cutoff)) continue;
      r.setLastReminderAt(LocalDateTime.now());
      r.setReminderCount(r.getReminderCount() + 1);
      SponsorshipReservation saved = repository.save(r);
      afterCommit(() -> mail.sendReminder(saved));
      sent++;
    }
    return sent;
  }

  // ---------------------------------------------------------------- helpers

  static LocalDateTime minusBusinessDays(LocalDateTime from, int days) {
    LocalDateTime t = from;
    int left = days;
    while (left > 0) {
      t = t.minusDays(1);
      java.time.DayOfWeek d = t.getDayOfWeek();
      if (d != java.time.DayOfWeek.SATURDAY && d != java.time.DayOfWeek.SUNDAY) left--;
    }
    return t;
  }

  private SponsorshipReservation load(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
  }

  private String newReference() {
    for (int i = 0; i < 20; i++) {
      StringBuilder sb = new StringBuilder("RSV-");
      for (int k = 0; k < 6; k++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
      if (!repository.existsByReferenceIgnoreCase(sb.toString())) return sb.toString();
    }
    throw new IllegalStateException("Could not allocate a reservation reference");
  }

  static Confirmation toConfirmation(SponsorshipReservation r) {
    return Confirmation.builder()
        .reference(r.getReference())
        .packageName(r.getPackageName())
        .amount(r.getAmount())
        .currency(r.getCurrency())
        .status(r.getStatus().name())
        .email(r.getEmail())
        .bankAccounts(
            SponsorshipPaymentDetails.ACCOUNTS.stream()
                .map(a -> new BankAccount(a.bank(), a.number()))
                .toList())
        .payee(SponsorshipPaymentDetails.PAYEE)
        .build();
  }

  static AdminRow toRow(SponsorshipReservation r) {
    return AdminRow.builder()
        .id(r.getId())
        .reference(r.getReference())
        .sponsorshipId(r.getSponsorship() != null ? r.getSponsorship().getId() : null)
        .packageName(r.getPackageName())
        .packageType(r.getPackageType())
        .amount(r.getAmount())
        .currency(r.getCurrency())
        .company(r.getCompany())
        .contactName(r.getContactName())
        .email(r.getEmail())
        .phone(r.getPhone())
        .tin(r.getTin())
        .boothPreference(r.getBoothPreference())
        .message(r.getMessage())
        .locale(r.getLocale())
        .status(r.getStatus().name())
        .amountPaid(r.getAmountPaid())
        .adminNotes(r.getAdminNotes())
        .assignedTo(r.getAssignedTo())
        .source(r.getSource())
        .agreementSentAt(r.getAgreementSentAt())
        .signedAt(r.getSignedAt())
        .paidAt(r.getPaidAt())
        .lastReminderAt(r.getLastReminderAt())
        .reminderCount(r.getReminderCount())
        .statusChangedAt(r.getStatusChangedAt())
        .createdAt(r.getCreatedAt())
        .updatedAt(r.getUpdatedAt())
        .build();
  }

  private static void afterCommit(Runnable run) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              run.run();
            }
          });
    } else {
      run.run();
    }
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
