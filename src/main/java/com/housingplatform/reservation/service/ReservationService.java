package com.housingplatform.reservation.service;

import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.repository.SponsorshipRepository;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.domain.SponsorshipReservation.Status;
import com.housingplatform.reservation.dto.ReservationDtos;
import com.housingplatform.reservation.dto.ReservationDtos.AdminCreate;
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
import java.util.Set;
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
  private final BoothService boothService;
  private final org.springframework.beans.factory.ObjectProvider<ReservationPaymentService>
      paymentService;
  private final org.springframework.context.ApplicationEventPublisher events;
  private final ReservationOrganizationLinker linker;

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
    linker.link(saved, null);
    repository.save(saved);
    List<String> held = List.of();
    if (r.getBoothCodes() != null && !r.getBoothCodes().isEmpty()) {
      held = boothService.hold(saved, r.getBoothCodes());
    }
    afterCommit(() -> mail.sendConfirmation(saved, true));
    return toConfirmation(saved, held);
  }

  // ---------------------------------------------------------------- admin

  /** An admin enters a reservation taken by phone or in person. */
  @Transactional
  public AdminRow createManual(AdminCreate c, String by) {
    if (c.getSponsorshipId() == null) throw new BusinessException("Please choose a package");
    Sponsorship pkg =
        sponsorships
            .findById(c.getSponsorshipId())
            .orElseThrow(() -> new BusinessException("Unknown package"));
    Status status =
        c.getStatus() == null || c.getStatus().isBlank()
            ? Status.REQUESTED
            : Status.valueOf(c.getStatus().trim().toUpperCase(Locale.ROOT));
    BigDecimal amount =
        c.getAmount() != null && c.getAmount().signum() >= 0
            ? c.getAmount()
            : (pkg.getBasePrice() == null ? BigDecimal.ZERO : pkg.getBasePrice());
    BigDecimal paid =
        c.getAmountPaid() == null || c.getAmountPaid().signum() < 0
            ? BigDecimal.ZERO
            : c.getAmountPaid();
    LocalDateTime now = LocalDateTime.now();
    SponsorshipReservation saved =
        repository.save(
            SponsorshipReservation.builder()
                .reference(newReference())
                .sponsorship(pkg)
                .packageName(pkg.getName())
                .packageType(pkg.getType() != null ? pkg.getType().name() : null)
                .amount(amount)
                .currency("ETB")
                .company(c.getCompany().trim())
                .contactName(c.getContactName().trim())
                .email(c.getEmail().trim())
                .phone(blankToNull(c.getPhone()))
                .tin(blankToNull(c.getTin()))
                .boothPreference(blankToNull(c.getBoothPreference()))
                .message(blankToNull(c.getMessage()))
                .locale(MailLocale.normalize(c.getLocale()))
                .status(status)
                .amountPaid(paid)
                .adminNotes(blankToNull(c.getAdminNotes()))
                .assignedTo(blankToNull(c.getAssignedTo()) == null ? by : c.getAssignedTo().trim())
                .source("manual")
                .reminderCount(0)
                .statusChangedAt(now)
                .agreementSentAt(
                    status.ordinal() >= Status.AGREEMENT_SENT.ordinal()
                            && status != Status.CANCELLED
                        ? now
                        : null)
                .signedAt(
                    status == Status.SIGNED
                            || status == Status.DEPOSIT_PAID
                            || status == Status.PAID
                        ? now
                        : null)
                .paidAt(status == Status.PAID ? now : null)
                .build());
    linker.link(saved, c.getOrganizationId());
    repository.save(saved);
    if (c.getBoothCodes() != null && !c.getBoothCodes().isEmpty()) {
      boothService.hold(saved, c.getBoothCodes());
      if (status == Status.PAID) boothService.sell(saved.getId());
    }
    paid(saved);
    if (c.isSendEmail() && status != Status.CANCELLED) {
      afterCommit(() -> mail.sendConfirmation(saved, status == Status.REQUESTED, false));
    }
    return row(saved);
  }

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
    return repository.findAll(spec, sorted).map(this::row);
  }

  private AdminRow row(SponsorshipReservation r) {
    return toRow(r, boothService.codesFor(r.getId()));
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
        .booths(boothService.counts())
        .build();
  }

  @Transactional
  public AdminRow update(UUID id, AdminUpdate u) {
    SponsorshipReservation r = load(id);
    Status before = r.getStatus();
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
    SponsorshipReservation saved = repository.save(r);
    if (saved.getStatus() == Status.PAID) boothService.sell(saved.getId());
    if (saved.getStatus() == Status.CANCELLED) boothService.release(saved.getId());
    if (saved.getStatus() != before) paid(saved);
    return row(saved);
  }

  /** Admin assigns (or replaces) the booths of a reservation. */
  @Transactional
  public AdminRow assignBooths(UUID id, List<String> codes) {
    SponsorshipReservation r = load(id);
    if (!r.getStatus().open()) throw new BusinessException("The reservation is cancelled");
    boothService.hold(r, codes == null ? List.of() : codes);
    return row(r);
  }

  /** Frees booth holds that ran out and notes it on the reservation. Returns how many. */
  @Transactional
  public int expireBoothHolds() {
    Set<UUID> affected = boothService.expireHolds();
    for (UUID id : affected) {
      repository
          .findById(id)
          .ifPresent(
              r -> {
                String note = "Booth hold expired on " + LocalDateTime.now().withNano(0);
                r.setAdminNotes(r.getAdminNotes() == null ? note : r.getAdminNotes() + "\n" + note);
                repository.save(r);
              });
    }
    return affected.size();
  }

  public static LocalDateTime plusBusinessDays(LocalDateTime from, int days) {
    LocalDateTime t = from;
    int left = days;
    while (left > 0) {
      t = t.plusDays(1);
      java.time.DayOfWeek d = t.getDayOfWeek();
      if (d != java.time.DayOfWeek.SATURDAY && d != java.time.DayOfWeek.SUNDAY) left--;
    }
    return t;
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
    return row(saved);
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

  Confirmation toConfirmation(SponsorshipReservation r, List<String> boothCodes) {
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
        .boothCodes(boothCodes)
        .boothsIncluded(BoothService.boothsForPackage(r.getPackageType()))
        .depositAmount(
            paymentService.getIfAvailable() == null
                ? null
                : paymentService.getIfAvailable().depositFor(r))
        .checkoutAvailable(
            paymentService.getIfAvailable() != null
                && paymentService.getIfAvailable().checkoutAvailable())
        .build();
  }

  static AdminRow toRow(SponsorshipReservation r, List<String> boothCodes) {
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
        .boothCodes(boothCodes)
        .boothsIncluded(BoothService.boothsForPackage(r.getPackageType()))
        .organizationId(r.getOrganization() != null ? r.getOrganization().getId() : null)
        .organizationName(r.getOrganization() != null ? r.getOrganization().getName() : null)
        .exhibitorInvitedAt(r.getExhibitorInvitedAt())
        .build();
  }

  /** Reservations linked to the signed-in user's organization, newest first. */
  @Transactional(readOnly = true)
  public List<ReservationDtos.ExhibitorRow> forOrganization(UUID organizationId) {
    return repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(
            r ->
                ReservationDtos.ExhibitorRow.builder()
                    .reference(r.getReference())
                    .packageName(r.getPackageName())
                    .packageType(r.getPackageType())
                    .amount(r.getAmount())
                    .amountPaid(r.getAmountPaid())
                    .currency(r.getCurrency())
                    .status(r.getStatus().name())
                    .boothCodes(boothService.codesFor(r.getId()))
                    .boothsIncluded(BoothService.boothsForPackage(r.getPackageType()))
                    .email(r.getEmail())
                    .paidAt(r.getPaidAt())
                    .build())
        .toList();
  }

  /** Fires the exhibitor-provisioning listener once the surrounding transaction commits. */
  private void paid(SponsorshipReservation r) {
    if (r.getStatus() == Status.PAID || r.getStatus() == Status.DEPOSIT_PAID) {
      events.publishEvent(new ReservationPaidEvent(r.getId()));
    }
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
