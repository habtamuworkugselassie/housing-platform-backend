package com.housingplatform.exhibition.api;

import com.housingplatform.exhibition.dto.LiveBroadcastResponse;
import com.housingplatform.exhibition.dto.LiveGoLiveRequest;
import com.housingplatform.exhibition.dto.LiveTokenResponse;
import com.housingplatform.exhibition.service.LiveBroadcastService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public + broadcaster live-broadcast endpoints (gated by approval, not by URL security). */
@RestController
@RequestMapping("/api/v1/exhibition/live")
@Tag(name = "Exhibition", description = "Live broadcasting (public)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class LiveBroadcastController {

  private final LiveBroadcastService service;

  @PostMapping("/request")
  @Operation(
      summary = "Request to go live",
      description = "Creates a pending request. A publish token is issued only after an organizer approves.")
  public ResponseEntity<LiveBroadcastResponse> requestGoLive(
      @Valid @RequestBody LiveGoLiveRequest request, HttpServletRequest http) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.requestGoLive(request, clientIp(http)));
  }

  @GetMapping
  @Operation(summary = "List live broadcasts", description = "Public wall of currently-live streams.")
  public ResponseEntity<List<LiveBroadcastResponse>> listLive() {
    return ResponseEntity.ok(service.listLive());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a broadcast's public status", description = "Used to poll for approval.")
  public ResponseEntity<LiveBroadcastResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(service.get(id));
  }

  @GetMapping("/{id}/publish-token")
  @Operation(summary = "Broadcaster publish token", description = "Only when the request is approved.")
  public ResponseEntity<LiveTokenResponse> publishToken(
      @PathVariable UUID id, HttpServletRequest http) {
    return ResponseEntity.ok(service.publishToken(id, clientIp(http)));
  }

  @GetMapping("/{id}/viewer-token")
  @Operation(summary = "Viewer token", description = "Subscribe-only token for a live broadcast.")
  public ResponseEntity<LiveTokenResponse> viewerToken(
      @PathVariable UUID id, @RequestParam(name = "name", required = false) String name) {
    return ResponseEntity.ok(service.viewerToken(id, name));
  }

  private static String clientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }
}
