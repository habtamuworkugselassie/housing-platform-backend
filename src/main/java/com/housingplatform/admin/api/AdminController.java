package com.housingplatform.admin.api;

import com.housingplatform.admin.dto.AdminStatsResponse;
import com.housingplatform.admin.service.AdminService;
import com.housingplatform.identity.dto.AdminOrganizationCreateRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Admin management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminController {

  private final AdminService adminService;
  private final OrganizationService organizationService;

  @GetMapping("/stats")
  @Operation(
      summary = "Get admin dashboard statistics",
      description = "Retrieve statistics for the admin dashboard (admin only)")
  public ResponseEntity<AdminStatsResponse> getStats() {
    AdminStatsResponse stats = adminService.getStats();
    return ResponseEntity.ok(stats);
  }

  @PostMapping("/organizations")
  @Operation(
      summary = "Register organization (admin)",
      description =
          "Create a new organization (REAL_ESTATE_COMPANY, BANK, SUPPLIER, CONTRACTOR, DEVELOPER, INSURANCE, CONSULTANT_ARCHITECT, FINISHING_CONTRACTOR, MEDIA_COMPANY) with full details. Optionally set initial status (e.g. APPROVED).")
  public ResponseEntity<OrganizationResponse> registerOrganization(
      @Valid @RequestBody AdminOrganizationCreateRequest request) {
    OrganizationResponse created = organizationService.createOrganizationAsAdmin(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }
}
