package com.housingplatform.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housingplatform.reservation.domain.Booth;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.repository.BoothRepository;
import com.housingplatform.reservation.service.BoothService;
import com.housingplatform.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoothServiceTest {

  private final BoothRepository repo = mock(BoothRepository.class);
  private final BoothService service = new BoothService(repo);

  private static Booth booth(String code, Booth.Status status) {
    Booth b =
        Booth.builder()
            .code(code)
            .block("B")
            .mapCol(3)
            .mapRow(1)
            .boothType(Booth.Type.CORNER)
            .sizeSqm(9)
            .status(status)
            .build();
    b.setId(UUID.randomUUID());
    return b;
  }

  private static SponsorshipReservation reservation(String type) {
    SponsorshipReservation r =
        SponsorshipReservation.builder()
            .reference("RSV-A")
            .packageName("x")
            .packageType(type)
            .amount(BigDecimal.TEN)
            .currency("ETB")
            .company("c")
            .contactName("n")
            .email("e@x")
            .locale("en")
            .status(SponsorshipReservation.Status.REQUESTED)
            .amountPaid(BigDecimal.ZERO)
            .statusChangedAt(LocalDateTime.now())
            .build();
    r.setId(UUID.randomUUID());
    return r;
  }

  @Test
  void packageBoothCountsFollowTheBoothSizes() {
    assertThat(BoothService.boothsForPackage("EXCLUSIVE")).isEqualTo(6);
    assertThat(BoothService.boothsForPackage("PLATINUM")).isEqualTo(3);
    assertThat(BoothService.boothsForPackage("GOLD")).isEqualTo(2);
    assertThat(BoothService.boothsForPackage("SILVER")).isEqualTo(1);
    assertThat(BoothService.boothsForPackage("SPECIAL")).isZero();
  }

  @Test
  void holdsAvailableBoothsForTenBusinessDaysAndRefusesTooMany() {
    SponsorshipReservation gold = reservation("GOLD");
    Booth b101 = booth("B101", Booth.Status.AVAILABLE);
    Booth b102 = booth("B102", Booth.Status.AVAILABLE);
    when(repo.findByReservationId(gold.getId())).thenReturn(List.of());
    when(repo.findByCodeInIgnoreCase(anyCollection())).thenReturn(List.of(b101, b102));
    when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    List<String> held = service.hold(gold, List.of(" b101", "B102 "));
    assertThat(held).containsExactly("B101", "B102");
    assertThat(b101.getStatus()).isEqualTo(Booth.Status.HELD);
    assertThat(b101.getReservation()).isSameAs(gold);
    assertThat(b101.getHoldExpiresAt()).isAfter(LocalDateTime.now().plusDays(11));

    assertThatThrownBy(() -> service.hold(gold, List.of("B101", "B102", "B103")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("2 booth");
  }

  @Test
  void refusesABoothSomeoneElseHolds() {
    SponsorshipReservation r = reservation("SILVER");
    Booth taken = booth("C101", Booth.Status.HELD);
    taken.setReservation(reservation("GOLD"));
    when(repo.findByReservationId(r.getId())).thenReturn(List.of());
    when(repo.findByCodeInIgnoreCase(anyCollection())).thenReturn(List.of(taken));
    assertThatThrownBy(() -> service.hold(r, List.of("C101")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("no longer available");
  }

  @Test
  void expiredHoldsAreFreedAndReportTheirReservations() {
    SponsorshipReservation r = reservation("GOLD");
    Booth old = booth("A101", Booth.Status.HELD);
    old.setReservation(r);
    old.setHoldExpiresAt(LocalDateTime.now().minusDays(1));
    when(repo.findByStatusAndHoldExpiresAtBefore(any(), any())).thenReturn(List.of(old));
    when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    assertThat(service.expireHolds()).containsExactly(r.getId());
    assertThat(old.getStatus()).isEqualTo(Booth.Status.AVAILABLE);
    assertThat(old.getReservation()).isNull();
  }

  @Test
  void premiumBoothsAreForTheTopTiersOnly() {
    assertThat(BoothService.allowedTypesFor("GOLD"))
        .containsExactlyInAnyOrder(Booth.Type.CORNER, Booth.Type.STANDARD);
    assertThat(BoothService.allowedTypesFor("SILVER")).containsExactly(Booth.Type.STANDARD);
    assertThat(BoothService.allowedTypesFor("PLATINUM")).hasSize(3);

    SponsorshipReservation gold = reservation("GOLD");
    Booth premium = booth("B101", Booth.Status.AVAILABLE);
    premium.setBoothType(Booth.Type.PREMIUM);
    when(repo.findByReservationId(gold.getId())).thenReturn(List.of());
    when(repo.findByCodeInIgnoreCase(anyCollection())).thenReturn(List.of(premium));
    assertThatThrownBy(() -> service.hold(gold, List.of("B101")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("premium booth");
    assertThat(premium.getStatus()).isEqualTo(Booth.Status.AVAILABLE);
  }
}
