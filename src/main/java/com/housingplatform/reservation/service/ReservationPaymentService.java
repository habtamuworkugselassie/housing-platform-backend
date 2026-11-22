package com.housingplatform.reservation.service;

import com.housingplatform.payment.chapa.ChapaClient;
import com.housingplatform.payment.chapa.ChapaClient.InitializeRequest;
import com.housingplatform.payment.chapa.ChapaClient.InitializeResult;
import com.housingplatform.payment.chapa.ChapaClient.VerifyResult;
import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.payment.chapa.ChapaProperties;
import com.housingplatform.reservation.domain.ReservationPayment;
import com.housingplatform.reservation.domain.ReservationPayment.Purpose;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.domain.SponsorshipReservation.Status;
import com.housingplatform.reservation.dto.ReservationDtos.CheckoutResponse;
import com.housingplatform.reservation.dto.ReservationDtos.PaymentView;
import com.housingplatform.reservation.dto.ReservationDtos.StatusView;
import com.housingplatform.reservation.repository.ReservationPaymentRepository;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays a reservation online through Chapa: a deposit to lock the booths, or the balance. The
 * reference plus the sponsor's email act as the key, since sponsors have no account.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationPaymentService {

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SponsorshipReservationRepository reservations;
  private final ReservationPaymentRepository payments;
  private final ChapaClient chapa;
  private final ChapaProperties chapaProperties;
  private final BoothService boothService;
  private final ReservationMailService mail;

  /** Share of the package price that locks the booths; the rest is due within 10 working days. */
  @Value("${app.reservation.deposit-percent:30}")
  private int depositPercent;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  public boolean checkoutAvailable() {
    return chapa.isConfigured();
  }

  public BigDecimal depositFor(SponsorshipReservation r) {
    BigDecimal pct = BigDecimal.valueOf(Math.max(1, Math.min(100, depositPercent)));
    return r.getAmount().multiply(pct).divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING);
  }

  public BigDecimal outstanding(SponsorshipReservation r) {
    BigDecimal due =
        r.getAmount().subtract(r.getAmountPaid() == null ? BigDecimal.ZERO : r.getAmountPaid());
    return due.signum() < 0 ? BigDecimal.ZERO : due;
  }

  /** The reservation as the sponsor may see it: reference + email is the key. */
  @Transactional
  public StatusView status(String reference, String email, boolean refresh) {
    SponsorshipReservation r = loadOwn(reference, email);
    if (refresh) {
      for (ReservationPayment p : payments.findByReservationIdOrderByCreatedAtDesc(r.getId())) {
        if (p.getStatus() == ReservationPayment.Status.PENDING && p.getTxRef() != null) {
          apply(p, chapa.verify(p.getTxRef()));
        }
      }
      r = reservations.findById(r.getId()).orElse(r);
    }
    return toStatus(r);
  }

  @Transactional
  public CheckoutResponse startCheckout(
      String reference, String email, Purpose purpose, ChapaPaymentMethod method) {
    SponsorshipReservation r = loadOwn(reference, email);
    if (!chapa.isConfigured())
      throw new BusinessException("Online payment is not configured on this server yet");
    if (!r.getStatus().open()) throw new BusinessException("This reservation is cancelled");
    if (r.getStatus() == Status.PAID || outstanding(r).signum() == 0) {
      throw new BusinessException("This reservation is already paid in full");
    }
    BigDecimal amount =
        purpose == Purpose.BALANCE ? outstanding(r) : depositFor(r).min(outstanding(r));
    if (purpose == Purpose.DEPOSIT
        && r.getAmountPaid() != null
        && r.getAmountPaid().compareTo(depositFor(r)) >= 0) {
      throw new BusinessException("The deposit is already paid; pay the balance instead");
    }
    // Resume a pending checkout for the same purpose if the provider has not reported on it.
    for (ReservationPayment p : payments.findByReservationIdOrderByCreatedAtDesc(r.getId())) {
      if (p.getStatus() == ReservationPayment.Status.PENDING
          && p.getPurpose() == purpose
          && p.getCheckoutUrl() != null) {
        VerifyResult v = chapa.verify(p.getTxRef());
        if (v.isSuccess()) {
          apply(p, v);
          throw new BusinessException("That payment has already gone through");
        }
        if (!v.isFailed()) return toCheckout(p);
        p.setStatus(ReservationPayment.Status.FAILED);
        p.setFailureReason("Previous attempt failed at the provider");
        payments.save(p);
      }
    }
    ChapaPaymentMethod chosen = method == null ? ChapaPaymentMethod.CARD : method;
    String txRef = newTxRef(r.getReference(), purpose);
    String[] names = splitName(r.getContactName());
    InitializeResult result =
        chapa.initialize(
            new InitializeRequest(
                amount,
                r.getCurrency(),
                txRef,
                r.getEmail(),
                names[0],
                names[1],
                r.getPhone(),
                callbackUrl(),
                returnUrl(r),
                (purpose == Purpose.DEPOSIT ? "Deposit " : "Balance ") + r.getReference(),
                r.getPackageName() + " - Ethio Build Connect Expo 2027 (" + r.getReference() + ")",
                chosen.preferredPaymentMethods()));
    ReservationPayment p =
        payments.save(
            ReservationPayment.builder()
                .reservation(r)
                .txRef(txRef)
                .purpose(purpose)
                .amount(amount)
                .currency(r.getCurrency())
                .status(ReservationPayment.Status.PENDING)
                .checkoutUrl(result.checkoutUrl())
                .method(chosen.name())
                .build());
    return toCheckout(p);
  }

  public boolean ownsTxRef(String txRef) {
    return txRef != null && payments.findByTxRef(txRef).isPresent();
  }

  @Transactional
  public void handleProviderNotification(String txRef) {
    ReservationPayment p =
        payments
            .findByTxRef(txRef)
            .orElseThrow(() -> new ResourceNotFoundException("ReservationPayment", txRef));
    if (p.getStatus() != ReservationPayment.Status.PENDING) return;
    apply(p, chapa.verify(txRef));
  }

  /** Chapa webhooks can be missed; ask about pending checkouts every so often. */
  @Scheduled(
      fixedDelayString = "${app.reservation.reconcile-ms:900000}",
      initialDelayString = "180000")
  @Transactional
  public void reconcilePending() {
    if (!chapa.isConfigured()) return;
    for (ReservationPayment p : payments.findByStatus(ReservationPayment.Status.PENDING)) {
      if (p.getCreatedAt() != null && p.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(5)))
        continue;
      try {
        apply(p, chapa.verify(p.getTxRef()));
      } catch (RuntimeException e) {
        log.warn("Reconcile of {} failed: {}", p.getTxRef(), e.getMessage());
      }
    }
  }

  void apply(ReservationPayment p, VerifyResult v) {
    if (v == null || p.getStatus() != ReservationPayment.Status.PENDING) return;
    if (v.isSuccess()) {
      BigDecimal paidAmount = v.amount() != null ? v.amount() : p.getAmount();
      p.setStatus(ReservationPayment.Status.PAID);
      p.setPaidAt(LocalDateTime.now());
      p.setProviderReference(v.reference());
      if (v.method() != null) p.setMethod(v.method());
      payments.save(p);
      SponsorshipReservation r = p.getReservation();
      BigDecimal total =
          (r.getAmountPaid() == null ? BigDecimal.ZERO : r.getAmountPaid()).add(paidAmount);
      r.setAmountPaid(total);
      LocalDateTime now = LocalDateTime.now();
      if (total.compareTo(r.getAmount()) >= 0) {
        r.setStatus(Status.PAID);
        r.setPaidAt(now);
        boothService.sell(r.getId());
      } else if (r.getStatus() != Status.PAID) {
        r.setStatus(Status.DEPOSIT_PAID);
        boothService.firmUp(r.getId());
      }
      r.setStatusChangedAt(now);
      String note =
          "Online "
              + p.getPurpose().name().toLowerCase(Locale.ROOT)
              + " of "
              + paidAmount
              + " "
              + p.getCurrency()
              + " received "
              + now.withNano(0)
              + " ("
              + p.getTxRef()
              + ")";
      r.setAdminNotes(r.getAdminNotes() == null ? note : r.getAdminNotes() + "\n" + note);
      reservations.save(r);
      mail.sendPaymentReceipt(r, p);
      log.info(
          "Reservation {} paid {} {} online ({})",
          r.getReference(),
          paidAmount,
          p.getCurrency(),
          p.getPurpose());
    } else if (v.isFailed()) {
      p.setStatus(ReservationPayment.Status.FAILED);
      p.setFailureReason("Declined or cancelled at the provider");
      payments.save(p);
    }
  }

  // ---------------------------------------------------------------- helpers

  private SponsorshipReservation loadOwn(String reference, String email) {
    SponsorshipReservation r =
        reservations
            .findByReferenceIgnoreCase(reference == null ? "" : reference.trim())
            .orElseThrow(() -> new ResourceNotFoundException("Reservation", reference));
    if (email == null || !email.trim().equalsIgnoreCase(r.getEmail())) {
      throw new ResourceNotFoundException("Reservation", reference);
    }
    return r;
  }

  private String newTxRef(String reference, Purpose purpose) {
    // Chapa allows 20 characters: RSV-XXXXXX (10) + -D- + 4 random = 17.
    StringBuilder sb =
        new StringBuilder(reference).append(purpose == Purpose.DEPOSIT ? "-D-" : "-B-");
    for (int i = 0; i < 4; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    return sb.toString();
  }

  private static String[] splitName(String full) {
    String f = full == null ? "" : full.trim();
    int sp = f.indexOf(' ');
    return sp > 0 ? new String[] {f.substring(0, sp), f.substring(sp + 1)} : new String[] {f, "-"};
  }

  private String callbackUrl() {
    String base = chapaProperties.getPublicApiBaseUrl();
    if (base == null || base.isBlank()) return null;
    return base.replaceAll("/$", "") + "/api/v1/payments/chapa/webhook";
  }

  private String returnUrl(SponsorshipReservation r) {
    String base = frontendBaseUrl.replaceAll("/$", "");
    String prefix =
        com.housingplatform.shared.mail.MailLocale.isAmharic(r.getLocale()) ? base + "/am" : base;
    return prefix
        + "/reserve/"
        + r.getReference()
        + "?email="
        + java.net.URLEncoder.encode(r.getEmail(), java.nio.charset.StandardCharsets.UTF_8)
        + "&return=1";
  }

  private CheckoutResponse toCheckout(ReservationPayment p) {
    return CheckoutResponse.builder()
        .txRef(p.getTxRef())
        .checkoutUrl(p.getCheckoutUrl())
        .amount(p.getAmount())
        .currency(p.getCurrency())
        .purpose(p.getPurpose().name())
        .build();
  }

  StatusView toStatus(SponsorshipReservation r) {
    List<PaymentView> list =
        payments.findByReservationIdOrderByCreatedAtDesc(r.getId()).stream()
            .map(
                p ->
                    PaymentView.builder()
                        .txRef(p.getTxRef())
                        .purpose(p.getPurpose().name())
                        .amount(p.getAmount())
                        .currency(p.getCurrency())
                        .status(p.getStatus().name())
                        .checkoutUrl(
                            p.getStatus() == ReservationPayment.Status.PENDING
                                ? p.getCheckoutUrl()
                                : null)
                        .method(p.getMethod())
                        .paidAt(p.getPaidAt())
                        .createdAt(p.getCreatedAt())
                        .build())
            .toList();
    BigDecimal paid = r.getAmountPaid() == null ? BigDecimal.ZERO : r.getAmountPaid();
    boolean depositDone = paid.compareTo(depositFor(r)) >= 0;
    return StatusView.builder()
        .reference(r.getReference())
        .packageName(r.getPackageName())
        .company(r.getCompany())
        .contactName(r.getContactName())
        .amount(r.getAmount())
        .amountPaid(paid)
        .outstanding(outstanding(r))
        .depositAmount(depositFor(r))
        .depositPercent(depositPercent)
        .depositPaid(depositDone)
        .currency(r.getCurrency())
        .status(r.getStatus().name())
        .locale(r.getLocale())
        .boothCodes(boothService.codesFor(r.getId()))
        .checkoutAvailable(
            chapa.isConfigured() && r.getStatus().open() && outstanding(r).signum() > 0)
        .payments(list)
        .bankAccounts(
            SponsorshipPaymentDetails.ACCOUNTS.stream()
                .map(
                    a ->
                        new com.housingplatform.reservation.dto.ReservationDtos.BankAccount(
                            a.bank(), a.number()))
                .toList())
        .payee(SponsorshipPaymentDetails.PAYEE)
        .build();
  }
}
