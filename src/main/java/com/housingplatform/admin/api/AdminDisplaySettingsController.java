package com.housingplatform.admin.api;

import com.housingplatform.shared.dto.DisplaySettingsResponse;
import com.housingplatform.shared.dto.DisplaySettingsUpdateRequest;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import com.housingplatform.shared.service.DisplaySettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/display-settings")
@Tag(name = "Admin display settings", description = "Configure public landing page timings")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminDisplaySettingsController {

  private final DisplaySettingsService displaySettingsService;

  @GetMapping
  @Operation(summary = "Get display timings (admin)")
  public ResponseEntity<DisplaySettingsResponse> getDisplaySettings() {
    return ResponseEntity.ok(displaySettingsService.getDisplaySettings());
  }

  @PutMapping
  @Operation(summary = "Update display timings")
  public ResponseEntity<DisplaySettingsResponse> updateDisplaySettings(
      @Valid @RequestBody DisplaySettingsUpdateRequest request) {
    return ResponseEntity.ok(displaySettingsService.updateDisplaySettings(request));
  }
}
