package com.housingplatform.admin.api;

import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.dto.ReservationDtos.AdminRow;
import com.housingplatform.reservation.dto.ReservationDtos.AdminUpdate;
import com.housingplatform.reservation.dto.ReservationDtos.Pipeline;
import com.housingplatform.reservation.service.ReservationService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/sponsorship-reservations")
@Tag(name = "Admin - Sponsorship reservations", description = "The sponsor sales pipeline")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminReservationController {

  private final ReservationService service;
  private final com.housingplatform.reservation.service.BoothService boothService;
  private final com.housingplatform.reservation.service.ExhibitorProvisioningService provisioning;
  private final com.housingplatform.reservation.service.ReservationOrganizationLinker linker;

  @GetMapping("/booths")
  @Operation(summary = "Every booth with who holds it")
  public ResponseEntity<
          java.util.List<com.housingplatform.reservation.dto.ReservationDtos.BoothView>>
      booths() {
    return ResponseEntity.ok(boothService.list());
  }

  @PatchMapping("/booths/{code}")
  @Operation(summary = "Change a booth's status, type or notes")
  public ResponseEntity<com.housingplatform.reservation.dto.ReservationDtos.BoothView> updateBooth(
      @PathVariable String code,
      @RequestBody com.housingplatform.reservation.dto.ReservationDtos.BoothUpdate body) {
    return ResponseEntity.ok(boothService.update(code, body));
  }

  @PutMapping("/{id}/booths")
  @Operation(summary = "Assign (replace) the booths held by a reservation")
  public ResponseEntity<AdminRow> assignBooths(
      @PathVariable UUID id,
      @RequestBody com.housingplatform.reservation.dto.ReservationDtos.BoothAssign body) {
    return ResponseEntity.ok(service.assignBooths(id, body.getCodes()));
  }

  @GetMapping("/pipeline")
  @Operation(summary = "Counts and ETB totals per status")
  public ResponseEntity<Pipeline> pipeline() {
    return ResponseEntity.ok(service.pipeline());
  }

  @GetMapping
  @Operation(summary = "Search reservations")
  public ResponseEntity<Page<AdminRow>> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) SponsorshipReservation.Status status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok(service.search(q, status, PageRequest.of(page, Math.min(size, 200))));
  }

  @PostMapping
  @Operation(summary = "Enter a reservation taken by phone, email or in person")
  public ResponseEntity<AdminRow> create(
      @jakarta.validation.Valid @RequestBody
          com.housingplatform.reservation.dto.ReservationDtos.AdminCreate body,
      java.security.Principal principal) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(service.createManual(body, principal == null ? null : principal.getName()));
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update status, amount paid, notes, owner or booth")
  public ResponseEntity<AdminRow> update(@PathVariable UUID id, @RequestBody AdminUpdate body) {
    return ResponseEntity.ok(service.update(id, body));
  }

  @PostMapping("/{id}/send-agreement")
  @Operation(summary = "Email the agreement, package sheet and bank details again")
  public ResponseEntity<Void> sendAgreement(@PathVariable UUID id) {
    service.sendAgreement(id);
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/link-organizations")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Attach an organization to every reservation that still has none")
  public ResponseEntity<java.util.Map<String, Integer>> linkOrganizations() {
    return ResponseEntity.ok(java.util.Map.of("linked", linker.linkAllMissing()));
  }

  @PostMapping("/{id}/exhibitor-account")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Create (or re-invite) the exhibitor account for a paid reservation")
  public ResponseEntity<AdminRow> exhibitorAccount(@PathVariable UUID id) {
    return ResponseEntity.ok(provisioning.provision(id, true));
  }

  @PostMapping("/{id}/remind")
  @Operation(summary = "Send a reminder now")
  public ResponseEntity<AdminRow> remind(@PathVariable UUID id) {
    return ResponseEntity.ok(service.remind(id));
  }
}
