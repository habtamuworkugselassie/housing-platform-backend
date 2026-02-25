package com.housingplatform.identity.api;

import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
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

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Organization management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class OrganizationController {

  private final OrganizationService organizationService;

  @GetMapping
  @Operation(
      summary = "List organizations",
      description =
          "Retrieve all organizations with optional filtering. Admins can see all organizations.")
  public ResponseEntity<List<OrganizationResponse>> getAllOrganizations(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search) {
    // Normalize empty strings to null
    String normalizedType = (type != null && type.trim().isEmpty()) ? null : type;
    String normalizedStatus = (status != null && status.trim().isEmpty()) ? null : status;
    String normalizedSearch = (search != null && search.trim().isEmpty()) ? null : search;

    List<OrganizationResponse> organizations =
        organizationService.getAllOrganizations(normalizedType, normalizedStatus, normalizedSearch);
    return ResponseEntity.ok(organizations);
  }

  @GetMapping("/my-company")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @Operation(
      summary = "Get my organization",
      description = "Get the current user's real estate company (if they are a super agent)")
  public ResponseEntity<OrganizationResponse> getMyOrganization() {
    OrganizationResponse organization = organizationService.getMyOrganization();
    return ResponseEntity.ok(organization);
  }

  @GetMapping("/my-bank")
  @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
  @Operation(
      summary = "Get my bank",
      description =
          "Get the current user's bank organization (if they are a banker and primary contact)")
  public ResponseEntity<OrganizationResponse> getMyBank() {
    OrganizationResponse bank = organizationService.getMyBank();
    return ResponseEntity.ok(bank);
  }

  @GetMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get organization by ID",
      description = "Retrieve organization information by ID. Public access.")
  public ResponseEntity<OrganizationResponse> getOrganizationById(@PathVariable UUID id) {
    OrganizationResponse organization = organizationService.getOrganizationById(id);
    return ResponseEntity.ok(organization);
  }

  @PostMapping
  @Operation(
      summary = "Create organization",
      description = "Create a new organization (bank, real estate company, or supplier)")
  public ResponseEntity<OrganizationResponse> createOrganization(
      @Valid @RequestBody OrganizationRequest organizationRequest) {
    OrganizationResponse created = organizationService.createOrganization(organizationRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @AuthActionScope("organizations.update")
  @Operation(summary = "Update organization", description = "Update organization information")
  public ResponseEntity<OrganizationResponse> updateOrganization(
      @PathVariable UUID id, @Valid @RequestBody OrganizationRequest organizationRequest) {
    OrganizationResponse updated = organizationService.updateOrganization(id, organizationRequest);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/{id}/approve")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @AuthActionScope("organizations.approve")
  @Operation(summary = "Approve organization", description = "Approve an organization (admin only)")
  public ResponseEntity<OrganizationResponse> approveOrganization(@PathVariable UUID id) {
    OrganizationResponse approved = organizationService.approveOrganization(id);
    return ResponseEntity.ok(approved);
  }

  @PutMapping("/{id}/reject")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @AuthActionScope("organizations.reject")
  @Operation(summary = "Reject organization", description = "Reject an organization (admin only)")
  public ResponseEntity<OrganizationResponse> rejectOrganization(
      @PathVariable UUID id, @RequestBody(required = false) String reason) {
    OrganizationResponse rejected = organizationService.rejectOrganization(id, reason);
    return ResponseEntity.ok(rejected);
  }

  @PutMapping("/{id}/suspend")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @AuthActionScope("organizations.suspend")
  @Operation(summary = "Suspend organization", description = "Suspend an organization (admin only)")
  public ResponseEntity<OrganizationResponse> suspendOrganization(
      @PathVariable UUID id, @RequestBody(required = false) String reason) {
    OrganizationResponse suspended = organizationService.suspendOrganization(id, reason);
    return ResponseEntity.ok(suspended);
  }
}
