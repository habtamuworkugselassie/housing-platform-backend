package com.housingplatform.admin.api;

import com.housingplatform.exhibition.dto.AdminLiveBroadcastResponse;
import com.housingplatform.exhibition.service.LiveBroadcastService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Organizer moderation of live broadcasts: approve / reject go-live requests and cut live streams. */
@RestController
@RequestMapping("/api/v1/admin/exhibition/live")
@Tag(name = "Admin - Exhibition", description = "Moderate live broadcasts")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminLiveBroadcastController {

  private final LiveBroadcastService service;

  @GetMapping
  @Operation(summary = "List broadcasts", description = "Optionally filter by status.")
  public ResponseEntity<Page<AdminLiveBroadcastResponse>> list(
      @RequestParam(value = "status", required = false) String status,
      @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(service.adminList(status, pageable));
  }

  @PostMapping("/{id}/ingress")
  @Operation(
      summary = "Provision an ingress (organizer professional camera)",
      description = "Returns RTMP/WHIP connection details to paste into OBS or a hardware encoder.")
  public ResponseEntity<com.housingplatform.exhibition.dto.IngressResponse> createIngress(
      @PathVariable UUID id, @RequestParam(value = "type", defaultValue = "RTMP") String type) {
    return ResponseEntity.ok(service.createIngress(id, type));
  }

  @PostMapping("/{id}/simulcast")
  @Operation(
      summary = "Start a social simulcast",
      description =
          "Re-streams a LIVE broadcast to the given RTMP destinations (or all enabled ones) in real time.")
  public ResponseEntity<AdminLiveBroadcastResponse> startSimulcast(
      @PathVariable UUID id,
      @RequestBody(required = false)
          com.housingplatform.exhibition.dto.SimulcastStartRequest request) {
    return ResponseEntity.ok(
        service.startSimulcast(id, request == null ? null : request.targetIds()));
  }

  @DeleteMapping("/{id}/simulcast")
  @Operation(summary = "Stop the social simulcast")
  public ResponseEntity<AdminLiveBroadcastResponse> stopSimulcast(@PathVariable UUID id) {
    return ResponseEntity.ok(service.stopSimulcast(id));
  }

  @PutMapping("/{id}/approve")
  @Operation(summary = "Approve a go-live request")
  public ResponseEntity<AdminLiveBroadcastResponse> approve(@PathVariable UUID id) {
    return ResponseEntity.ok(service.approve(id));
  }

  @PutMapping("/{id}/reject")
  @Operation(summary = "Reject a go-live request")
  public ResponseEntity<AdminLiveBroadcastResponse> reject(@PathVariable UUID id) {
    return ResponseEntity.ok(service.reject(id));
  }

  @PutMapping("/{id}/end")
  @Operation(summary = "Cut a live broadcast (kill-switch)")
  public ResponseEntity<AdminLiveBroadcastResponse> end(@PathVariable UUID id) {
    return ResponseEntity.ok(service.end(id));
  }
}
