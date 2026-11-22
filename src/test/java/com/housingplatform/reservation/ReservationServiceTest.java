package com.housingplatform.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.repository.SponsorshipRepository;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.domain.SponsorshipReservation.Status;
import com.housingplatform.reservation.dto.ReservationDtos.AdminUpdate;
import com.housingplatform.reservation.dto.ReservationDtos.Confirmation;
import com.housingplatform.reservation.dto.ReservationDtos.ReserveRequest;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import com.housingplatform.reservation.service.ReservationMailService;
import com.housingplatform.reservation.service.ReservationService;
import com.housingplatform.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {

  private final SponsorshipReservationRepository repo =
      mock(SponsorshipReservationRepository.class);
  private final SponsorshipRepository packages = mock(SponsorshipRepository.class);
  private final ReservationMailService mail = mock(ReservationMailService.class);
  private final com.housingplatform.reservation.service.BoothService boothService =
      mock(com.housingplatform.reservation.service.BoothService.class);
  private final ReservationService service =
      new ReservationService(
          repo,
          packages,
          mail,
          boothService,
          new org.springframework.beans.factory.support.DefaultListableBeanFactory()
              .getBeanProvider(
                  com.housingplatform.reservation.service.ReservationPaymentService.class),
          mock(org.springframework.context.ApplicationEventPublisher.class));

  private static Sponsorship gold(UUID id) {
    Sponsorship s =
        Sponsorship.builder()
            .name("Tier 2 · Gold")
            .type(Sponsorship.SponsorshipType.GOLD)
            .basePrice(new BigDecimal("900000"))
            .status(Sponsorship.SponsorshipStatus.ACTIVE)
            .build();
    s.setId(id);
    return s;
  }

  private static ReserveRequest request(UUID pkg) {
    ReserveRequest r = new ReserveRequest();
    r.setSponsorshipId(pkg);
    r.setCompany("Noah Real Estate");
    r.setContactName("Sara Tesfaye");
    r.setEmail("sara@noah.et");
    r.setLocale("en");
    return r;
  }

  @Test
  void reservesAnActivePackageWithAReferenceAndEmailsTheAgreement() {
    UUID id = UUID.randomUUID();
    when(packages.findById(id)).thenReturn(Optional.of(gold(id)));
    when(repo.existsByReferenceIgnoreCase(anyString())).thenReturn(false);
    when(repo.save(any()))
        .thenAnswer(
            inv -> {
              SponsorshipReservation r = inv.getArgument(0);
              r.setId(UUID.randomUUID());
              return r;
            });

    Confirmation c = service.reserve(request(id));

    assertThat(c.getReference()).matches("RSV-[A-Z2-9]{6}");
    assertThat(c.getPackageName()).isEqualTo("Tier 2 · Gold");
    assertThat(c.getAmount()).isEqualByComparingTo("900000");
    assertThat(c.getBankAccounts()).hasSize(7);
    verify(mail).sendConfirmation(any(), anyBoolean());
  }

  @Test
  void refusesInactiveOrMissingPackages() {
    UUID id = UUID.randomUUID();
    Sponsorship off = gold(id);
    off.setStatus(Sponsorship.SponsorshipStatus.INACTIVE);
    when(packages.findById(id)).thenReturn(Optional.of(off));
    assertThatThrownBy(() -> service.reserve(request(id))).isInstanceOf(BusinessException.class);
    verify(repo, never()).save(any());
  }

  @Test
  void honeypotStoresNothing() {
    ReserveRequest r = request(UUID.randomUUID());
    r.setWebsite("spam");
    service.reserve(r);
    verify(repo, never()).save(any());
    verify(mail, never()).sendConfirmation(any(), anyBoolean());
  }

  @Test
  void amountPaidMovesTheStatusAlongThePipeline() {
    SponsorshipReservation r =
        SponsorshipReservation.builder()
            .reference("RSV-ABCDEF")
            .packageName("Gold")
            .amount(new BigDecimal("900000"))
            .currency("ETB")
            .company("Noah")
            .contactName("Sara")
            .email("s@x")
            .locale("en")
            .status(Status.AGREEMENT_SENT)
            .amountPaid(BigDecimal.ZERO)
            .statusChangedAt(LocalDateTime.now())
            .build();
    r.setId(UUID.randomUUID());
    when(repo.findById(r.getId())).thenReturn(Optional.of(r));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    AdminUpdate deposit = new AdminUpdate();
    deposit.setAmountPaid(new BigDecimal("300000"));
    assertThat(service.update(r.getId(), deposit).getStatus()).isEqualTo("DEPOSIT_PAID");

    AdminUpdate full = new AdminUpdate();
    full.setAmountPaid(new BigDecimal("900000"));
    assertThat(service.update(r.getId(), full).getStatus()).isEqualTo("PAID");
    assertThat(r.getPaidAt()).isNotNull();

    AdminUpdate signed = new AdminUpdate();
    signed.setStatus("signed");
    assertThat(service.update(r.getId(), signed).getStatus()).isEqualTo("SIGNED");
    assertThat(r.getSignedAt()).isNotNull();
  }

  @Test
  void dueRemindersSkipAlreadyRemindedAndCapAtTwo() {
    SponsorshipReservation quiet =
        SponsorshipReservation.builder()
            .reference("RSV-A")
            .packageName("Gold")
            .amount(BigDecimal.TEN)
            .currency("ETB")
            .company("A")
            .contactName("A")
            .email("a@x")
            .locale("en")
            .status(Status.REQUESTED)
            .amountPaid(BigDecimal.ZERO)
            .reminderCount(0)
            .statusChangedAt(LocalDateTime.now().minusDays(20))
            .build();
    SponsorshipReservation capped =
        SponsorshipReservation.builder()
            .reference("RSV-B")
            .packageName("Gold")
            .amount(BigDecimal.TEN)
            .currency("ETB")
            .company("B")
            .contactName("B")
            .email("b@x")
            .locale("en")
            .status(Status.REQUESTED)
            .amountPaid(BigDecimal.ZERO)
            .reminderCount(2)
            .statusChangedAt(LocalDateTime.now().minusDays(20))
            .build();
    SponsorshipReservation recent =
        SponsorshipReservation.builder()
            .reference("RSV-C")
            .packageName("Gold")
            .amount(BigDecimal.TEN)
            .currency("ETB")
            .company("C")
            .contactName("C")
            .email("c@x")
            .locale("en")
            .status(Status.REQUESTED)
            .amountPaid(BigDecimal.ZERO)
            .reminderCount(1)
            .lastReminderAt(LocalDateTime.now().minusDays(1))
            .statusChangedAt(LocalDateTime.now().minusDays(20))
            .build();
    when(repo.findByStatusInAndStatusChangedAtBefore(any(), any()))
        .thenReturn(List.of(quiet, capped, recent));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertThat(service.sendDueReminders(5, 2)).isEqualTo(1);
    assertThat(quiet.getReminderCount()).isEqualTo(1);
    verify(mail).sendReminder(quiet);
    verify(mail, never()).sendReminder(capped);
    verify(mail, never()).sendReminder(recent);
  }

  @Test
  void adminCanEnterAManualReservationAtAnyStage() {
    UUID id = UUID.randomUUID();
    when(packages.findById(id)).thenReturn(Optional.of(gold(id)));
    when(repo.existsByReferenceIgnoreCase(anyString())).thenReturn(false);
    when(repo.save(any()))
        .thenAnswer(
            inv -> {
              SponsorshipReservation r = inv.getArgument(0);
              r.setId(UUID.randomUUID());
              return r;
            });
    when(boothService.hold(any(), any())).thenReturn(List.of("B104"));
    when(boothService.codesFor(any())).thenReturn(List.of("B104"));

    com.housingplatform.reservation.dto.ReservationDtos.AdminCreate c =
        new com.housingplatform.reservation.dto.ReservationDtos.AdminCreate();
    c.setSponsorshipId(id);
    c.setCompany("Walk-in PLC");
    c.setContactName("Dawit");
    c.setEmail("dawit@walkin.et");
    c.setStatus("deposit_paid");
    c.setAmountPaid(new BigDecimal("300000"));
    c.setBoothCodes(List.of("B104"));
    c.setSendEmail(false);

    var row = service.createManual(c, "admin@ebc");
    assertThat(row.getReference()).startsWith("RSV-");
    assertThat(row.getStatus()).isEqualTo("DEPOSIT_PAID");
    assertThat(row.getAmountPaid()).isEqualByComparingTo("300000");
    assertThat(row.getSource()).isEqualTo("manual");
    assertThat(row.getAssignedTo()).isEqualTo("admin@ebc");
    assertThat(row.getBoothCodes()).containsExactly("B104");
    verify(mail, never()).sendConfirmation(any(), anyBoolean(), anyBoolean());
  }
}
