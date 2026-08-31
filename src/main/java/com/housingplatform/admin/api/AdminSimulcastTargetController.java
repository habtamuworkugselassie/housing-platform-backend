package com.housingplatform.admin.api;

import com.housingplatform.exhibition.dto.SimulcastTargetRequest;
import com.housingplatform.exhibition.dto.SimulcastTargetResponse;
import com.housingplatform.exhibition.service.SimulcastTargetService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Manage reusable social RTMP destinations (YouTube/Facebook/TikTok/…) for simulcasting. */
@RestController
@RequestMapping("/api/v1/admin/exhibition/simulcast-targets")
@Tag(name = "Admin - Exhibition", description = "Social simulcast destinations")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminSimulcastTargetController {

  private final SimulcastTargetService service;

  @GetMapping
  @Operation(summary = "List simulcast destinations")
  public ResponseEntity<List<SimulcastTargetResponse>> list() {
    return ResponseEntity.ok(service.list());
  }

  @PostMapping
  @Operation(summary = "Add a simulcast destination")
  public ResponseEntity<SimulcastTargetResponse> create(
      @Valid @RequestBody SimulcastTargetRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update a simulcast destination", description = "Leave the key blank to keep it.")
  public ResponseEntity<SimulcastTargetResponse> update(
      @PathVariable UUID id, @Valid @RequestBody SimulcastTargetRequest request) {
    return ResponseEntity.ok(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a simulcast destination")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
