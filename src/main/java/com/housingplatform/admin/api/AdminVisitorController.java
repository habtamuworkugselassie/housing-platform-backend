package com.housingplatform.admin.api;

import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import com.housingplatform.visitor.domain.VisitorRegistration;
import com.housingplatform.visitor.dto.VisitorDtos.AdminRow;
import com.housingplatform.visitor.dto.VisitorDtos.CheckInRequest;
import com.housingplatform.visitor.dto.VisitorDtos.Stats;
import com.housingplatform.visitor.service.VisitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/visitors")
@Tag(name = "Admin - Visitors", description = "Expo visitor registrations, check-in and export")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminVisitorController {

  private final VisitorService service;

  @GetMapping("/stats")
  @Operation(summary = "Registration and check-in counts")
  public ResponseEntity<Stats> stats() {
    return ResponseEntity.ok(service.stats());
  }

  @GetMapping
  @Operation(summary = "Search registrations")
  public ResponseEntity<Page<AdminRow>> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) VisitorRegistration.Status status,
      @RequestParam(required = false) VisitorRegistration.VisitorType type,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok(
        service.search(q, status, type, PageRequest.of(page, Math.min(size, 200))));
  }

  @PostMapping("/check-in")
  @Operation(summary = "Check a badge in by code or scanned URL")
  public ResponseEntity<AdminRow> checkIn(@RequestBody CheckInRequest body, Principal principal) {
    return ResponseEntity.ok(
        service.checkIn(body.getCode(), principal == null ? null : principal.getName()));
  }

  @PutMapping("/{id}/status")
  @Operation(summary = "Set status (undo a check-in, cancel, restore)")
  public ResponseEntity<AdminRow> setStatus(
      @PathVariable UUID id, @RequestBody Map<String, String> body) {
    return ResponseEntity.ok(
        service.setStatus(id, VisitorRegistration.Status.valueOf(body.get("status"))));
  }

  @PostMapping("/{id}/resend")
  @Operation(summary = "Email the badge again")
  public ResponseEntity<Void> resend(@PathVariable UUID id) {
    service.resend(id);
    return ResponseEntity.accepted().build();
  }

  @GetMapping(value = "/export.csv", produces = "text/csv")
  @Operation(summary = "All registrations as CSV")
  public ResponseEntity<byte[]> export() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expo-visitors.csv\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(service.exportCsv().getBytes(StandardCharsets.UTF_8));
  }
}
