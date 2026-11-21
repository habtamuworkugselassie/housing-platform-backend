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
