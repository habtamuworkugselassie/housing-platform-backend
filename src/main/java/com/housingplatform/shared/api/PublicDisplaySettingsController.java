package com.housingplatform.shared.api;

import com.housingplatform.shared.dto.DisplaySettingsResponse;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import com.housingplatform.shared.service.DisplaySettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/display-settings")
@Tag(
    name = "Public display settings",
    description = "Landing page timing configuration (read-only)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class PublicDisplaySettingsController {

  private final DisplaySettingsService displaySettingsService;

  @GetMapping
  @Operation(
      summary = "Get public display timings",
      description = "Milliseconds for sponsor carousel and sidebar rotations.")
  public ResponseEntity<DisplaySettingsResponse> getDisplaySettings() {
    return ResponseEntity.ok(displaySettingsService.getDisplaySettings());
  }
}
