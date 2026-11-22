package com.housingplatform.reservation.api;

import com.housingplatform.reservation.dto.ReservationDtos.Confirmation;
import com.housingplatform.reservation.dto.ReservationDtos.ReserveRequest;
import com.housingplatform.reservation.service.ReservationService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sponsorship-reservations")
@Tag(name = "Sponsorship reservations", description = "Reserve a sponsorship package (public)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class ReservationController {

  private final ReservationService service;
  private final com.housingplatform.reservation.service.ReservationPaymentService payments;
  private final com.housingplatform.identity.service.OrganizationService organizations;
  private final com.housingplatform.reservation.service.ExhibitorDirectoryService directory;

  @org.springframework.web.bind.annotation.GetMapping("/exhibitors")
  @Operation(summary = "Public exhibitor directory: confirmed exhibitors and sponsors with booths")
  public ResponseEntity<
          java.util.List<com.housingplatform.reservation.dto.ReservationDtos.ExhibitorCard>>
      exhibitors() {
    return ResponseEntity.ok(directory.list());
  }

  @org.springframework.web.bind.annotation.GetMapping("/mine")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "Reservations of the signed-in exhibitor's organization")
  public ResponseEntity<
          java.util.List<com.housingplatform.reservation.dto.ReservationDtos.ExhibitorRow>>
      mine() {
    return ResponseEntity.ok(
        organizations
            .getCurrentUserOrganization()
            .map(o -> service.forOrganization(o.getId()))
            .orElseGet(java.util.List::of));
  }

  @org.springframework.web.bind.annotation.GetMapping("/{reference}")
  @Operation(summary = "A reservation's status and payments (reference + the sponsor's email)")
  public ResponseEntity<com.housingplatform.reservation.dto.ReservationDtos.StatusView> status(
      @org.springframework.web.bind.annotation.PathVariable String reference,
      @org.springframework.web.bind.annotation.RequestParam String email,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false")
          boolean refresh) {
    return ResponseEntity.ok(payments.status(reference, email, refresh));
  }

  @PostMapping("/{reference}/checkout")
  @Operation(summary = "Start a Chapa checkout for the deposit or the balance")
  public ResponseEntity<com.housingplatform.reservation.dto.ReservationDtos.CheckoutResponse>
      checkout(
          @org.springframework.web.bind.annotation.PathVariable String reference,
          @Valid @RequestBody
              com.housingplatform.reservation.dto.ReservationDtos.CheckoutRequest body) {
    com.housingplatform.reservation.domain.ReservationPayment.Purpose purpose =
        body.getPurpose() == null || body.getPurpose().isBlank()
            ? com.housingplatform.reservation.domain.ReservationPayment.Purpose.DEPOSIT
            : com.housingplatform.reservation.domain.ReservationPayment.Purpose.valueOf(
                body.getPurpose().trim().toUpperCase());
    com.housingplatform.payment.chapa.ChapaPaymentMethod method = null;
    if (body.getMethod() != null && !body.getMethod().isBlank()) {
      method =
          com.housingplatform.payment.chapa.ChapaPaymentMethod.valueOf(
              body.getMethod().trim().toUpperCase());
    }
    return ResponseEntity.ok(payments.startCheckout(reference, body.getEmail(), purpose, method));
  }

  private final com.housingplatform.reservation.service.BoothService boothService;

  @org.springframework.web.bind.annotation.GetMapping("/booths")
  @Operation(summary = "The hall plan: every booth with its status (no sponsor details)")
  public ResponseEntity<com.housingplatform.reservation.dto.ReservationDtos.BoothMap> booths() {
    var views =
        boothService.list().stream()
            .map(
                v ->
                    com.housingplatform.reservation.dto.ReservationDtos.BoothView.builder()
                        .code(v.getCode())
                        .block(v.getBlock())
                        .col(v.getCol())
                        .row(v.getRow())
                        .boothType(v.getBoothType())
                        .sizeSqm(v.getSizeSqm())
                        .status(v.getStatus())
                        .build())
            .toList();
    return ResponseEntity.ok(
        com.housingplatform.reservation.dto.ReservationDtos.BoothMap.builder()
            .booths(views)
            .counts(boothService.counts())
            .holdBusinessDays(
                com.housingplatform.reservation.service.BoothService.HOLD_BUSINESS_DAYS)
            .build());
  }

  @PostMapping
  @Operation(
      summary = "Reserve a sponsorship package",
      description =
          "Public. Records the reservation, emails the agreement, package sheet and bank details to the sponsor, and notifies the team.")
  public ResponseEntity<Confirmation> reserve(@Valid @RequestBody ReserveRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.reserve(request));
  }
}
