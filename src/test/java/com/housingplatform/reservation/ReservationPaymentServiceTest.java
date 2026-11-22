package com.housingplatform.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.housingplatform.reservation.repository.ReservationPaymentRepository;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import com.housingplatform.reservation.service.BoothService;
import com.housingplatform.reservation.service.ReservationMailService;
import com.housingplatform.reservation.service.ReservationPaymentService;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ReservationPaymentServiceTest {

  private final SponsorshipReservationRepository reservations =
      mock(SponsorshipReservationRepository.class);
  private final ReservationPaymentRepository payments = mock(ReservationPaymentRepository.class);
  private final ChapaClient chapa = mock(ChapaClient.class);
  private final BoothService booths = mock(BoothService.class);
  private final ReservationMailService mail = mock(ReservationMailService.class);
  private final ReservationPaymentService service =
      new ReservationPaymentService(
          reservations, payments, chapa, new ChapaProperties(), booths, mail);

  private SponsorshipReservation gold() {
    SponsorshipReservation r =
        SponsorshipReservation.builder()
            .reference("RSV-K7M2PX")
            .packageName("Gold")
            .packageType("GOLD")
            .amount(new BigDecimal("900000"))
            .currency("ETB")
            .company("Noah")
            .contactName("Sara Tesfaye")
            .email("sara@noah.et")
            .phone("+251911000000")
            .locale("en")
            .status(Status.AGREEMENT_SENT)
            .amountPaid(BigDecimal.ZERO)
            .statusChangedAt(LocalDateTime.now())
            .build();
    r.setId(UUID.randomUUID());
    ReflectionTestUtils.setField(service, "depositPercent", 30);
    ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://ethiobuildconnect.et");
    when(reservations.findByReferenceIgnoreCase("RSV-K7M2PX")).thenReturn(Optional.of(r));
    when(reservations.findById(r.getId())).thenReturn(Optional.of(r));
    when(reservations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(payments.save(any()))
        .thenAnswer(
            inv -> {
              ReservationPayment p = inv.getArgument(0);
              if (p.getId() == null) p.setId(UUID.randomUUID());
              return p;
            });
    when(payments.findByReservationIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
    when(booths.codesFor(any())).thenReturn(List.of("B104", "B105"));
    return r;
  }

  @Test
  void depositIsThirtyPercentRoundedUpAndTheEmailIsTheKey() {
    SponsorshipReservation r = gold();
    assertThat(service.depositFor(r)).isEqualByComparingTo("270000");
    assertThatThrownBy(() -> service.status("RSV-K7M2PX", "someone@else.et", false))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(service.status("RSV-K7M2PX", "SARA@noah.et", false).getDepositAmount())
        .isEqualByComparingTo("270000");
  }

  @Test
  void startsADepositCheckoutWithChapaAndReturnsToTheReservationPage() {
    gold();
    when(chapa.isConfigured()).thenReturn(true);
    when(chapa.initialize(any())).thenReturn(new InitializeResult("https://checkout.chapa.co/x"));

    CheckoutResponse c =
        service.startCheckout(
            "RSV-K7M2PX", "sara@noah.et", Purpose.DEPOSIT, ChapaPaymentMethod.TELEBIRR);

    assertThat(c.getCheckoutUrl()).isEqualTo("https://checkout.chapa.co/x");
    assertThat(c.getAmount()).isEqualByComparingTo("270000");
    assertThat(c.getTxRef()).matches("RSV-K7M2PX-D-[A-Z2-9]{4}");
    ArgumentCaptor<InitializeRequest> req = ArgumentCaptor.forClass(InitializeRequest.class);
    verify(chapa).initialize(req.capture());
    assertThat(req.getValue().returnUrl())
        .startsWith("https://ethiobuildconnect.et/reserve/RSV-K7M2PX?email=sara%40noah.et");
    assertThat(req.getValue().preferredPaymentMethods()).containsExactly("telebirr");
    assertThat(req.getValue().firstName()).isEqualTo("Sara");
  }

  @Test
  void aSuccessfulDepositFirmsTheBoothsAndAFullPaymentSellsThem() {
    SponsorshipReservation r = gold();
    ReservationPayment dep =
        ReservationPayment.builder()
            .reservation(r)
            .txRef("RSV-K7M2PX-D-AAAA")
            .purpose(Purpose.DEPOSIT)
            .amount(new BigDecimal("270000"))
            .currency("ETB")
            .status(ReservationPayment.Status.PENDING)
            .build();
    when(payments.findByTxRef("RSV-K7M2PX-D-AAAA")).thenReturn(Optional.of(dep));
    when(chapa.verify("RSV-K7M2PX-D-AAAA"))
        .thenReturn(
            new VerifyResult(
                "success",
                new BigDecimal("270000"),
                "ETB",
                "chapa-1",
                "RSV-K7M2PX-D-AAAA",
                "telebirr",
                "mobile",
                null));

    service.handleProviderNotification("RSV-K7M2PX-D-AAAA");

    assertThat(r.getStatus()).isEqualTo(Status.DEPOSIT_PAID);
    assertThat(r.getAmountPaid()).isEqualByComparingTo("270000");
    verify(booths).firmUp(r.getId());
    verify(mail).sendPaymentReceipt(r, dep);

    ReservationPayment bal =
        ReservationPayment.builder()
            .reservation(r)
            .txRef("RSV-K7M2PX-B-BBBB")
            .purpose(Purpose.BALANCE)
            .amount(new BigDecimal("630000"))
            .currency("ETB")
            .status(ReservationPayment.Status.PENDING)
            .build();
    when(payments.findByTxRef("RSV-K7M2PX-B-BBBB")).thenReturn(Optional.of(bal));
    when(chapa.verify("RSV-K7M2PX-B-BBBB"))
        .thenReturn(
            new VerifyResult(
                "success",
                new BigDecimal("630000"),
                "ETB",
                "chapa-2",
                "RSV-K7M2PX-B-BBBB",
                "card",
                "card",
                null));
    service.handleProviderNotification("RSV-K7M2PX-B-BBBB");
    assertThat(r.getStatus()).isEqualTo(Status.PAID);
    verify(booths).sell(r.getId());

    // Webhooks repeat; a settled payment is left alone.
    service.handleProviderNotification("RSV-K7M2PX-B-BBBB");
    assertThat(r.getAmountPaid()).isEqualByComparingTo("900000");
  }

  @Test
  void aFailedCheckoutIsRecordedWithoutTouchingTheReservation() {
    SponsorshipReservation r = gold();
    ReservationPayment dep =
        ReservationPayment.builder()
            .reservation(r)
            .txRef("RSV-K7M2PX-D-CCCC")
            .purpose(Purpose.DEPOSIT)
            .amount(new BigDecimal("270000"))
            .currency("ETB")
            .status(ReservationPayment.Status.PENDING)
            .build();
    when(payments.findByTxRef(anyString())).thenReturn(Optional.of(dep));
    when(chapa.verify(anyString()))
        .thenReturn(
            new VerifyResult("failed", null, null, null, "RSV-K7M2PX-D-CCCC", null, null, null));
    service.handleProviderNotification("RSV-K7M2PX-D-CCCC");
    assertThat(dep.getStatus()).isEqualTo(ReservationPayment.Status.FAILED);
    assertThat(r.getStatus()).isEqualTo(Status.AGREEMENT_SENT);
    assertThat(r.getAmountPaid()).isEqualByComparingTo("0");
  }
}
