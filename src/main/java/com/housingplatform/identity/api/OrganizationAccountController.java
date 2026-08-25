package com.housingplatform.identity.api;

import com.housingplatform.identity.dto.CreateOrganizationAccountRequest;
import com.housingplatform.identity.dto.OrganizationAccountResponse;
import com.housingplatform.identity.dto.SetAccountPasswordRequest;
import com.housingplatform.identity.dto.UpdateAccountStatusRequest;
import com.housingplatform.identity.service.OrganizationAccountService;
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

/**
 * Sponsor-company credential management. Any admin may see which logins a company has; only a super
 * admin may issue one, change its password, or take it away.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/users")
@Tag(name = "Organization Accounts", description = "Sponsor company login management (admin)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class OrganizationAccountController {

  private final OrganizationAccountService organizationAccountService;

  @GetMapping
  @Operation(
      summary = "List company accounts",
      description = "All logins linked to this organization, primary contact first (admin).")
  public ResponseEntity<List<OrganizationAccountResponse>> getAccounts(
      @PathVariable UUID organizationId) {
    return ResponseEntity.ok(organizationAccountService.getAccounts(organizationId));
  }

  @PostMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.SUPER_ADMIN_SECURED)
  @AuthActionScope("organizations.accounts.create")
  @Operation(
      summary = "Create company account",
      description =
          "Creates a login for this sponsor company with a password chosen by the super admin. The"
              + " portal role follows the organization type.")
  public ResponseEntity<OrganizationAccountResponse> createAccount(
      @PathVariable UUID organizationId,
      @Valid @RequestBody CreateOrganizationAccountRequest request) {
    OrganizationAccountResponse created =
        organizationAccountService.createAccount(organizationId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{userId}/password")
  @AuthPolicyScope(AuthPolicyScope.Policy.SUPER_ADMIN_SECURED)
  @AuthActionScope("organizations.accounts.password")
  @Operation(
      summary = "Set company account password",
      description =
          "Replaces the account's password. No email is sent; the operator hands it over.")
  public ResponseEntity<OrganizationAccountResponse> setPassword(
      @PathVariable UUID organizationId,
      @PathVariable UUID userId,
      @Valid @RequestBody SetAccountPasswordRequest request) {
    return ResponseEntity.ok(
        organizationAccountService.setPassword(organizationId, userId, request));
  }

  @PutMapping("/{userId}/status")
  @AuthPolicyScope(AuthPolicyScope.Policy.SUPER_ADMIN_SECURED)
  @AuthActionScope("organizations.accounts.status")
  @Operation(
      summary = "Set company account status",
      description = "Enable, disable or suspend a company login.")
  public ResponseEntity<OrganizationAccountResponse> setStatus(
      @PathVariable UUID organizationId,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateAccountStatusRequest request) {
    return ResponseEntity.ok(organizationAccountService.setStatus(organizationId, userId, request));
  }

  @PutMapping("/{userId}/primary-contact")
  @AuthPolicyScope(AuthPolicyScope.Policy.SUPER_ADMIN_SECURED)
  @AuthActionScope("organizations.accounts.primary")
  @Operation(
      summary = "Make primary contact",
      description =
          "Promotes this account to the organization's primary contact, and to super agent for a"
              + " real estate company.")
  public ResponseEntity<OrganizationAccountResponse> makePrimaryContact(
      @PathVariable UUID organizationId, @PathVariable UUID userId) {
    return ResponseEntity.ok(organizationAccountService.makePrimaryContact(organizationId, userId));
  }

  @DeleteMapping("/{userId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.SUPER_ADMIN_SECURED)
  @AuthActionScope("organizations.accounts.unlink")
  @Operation(
      summary = "Unlink company account",
      description =
          "Detaches the login from the company without deleting the person. Refused for the primary"
              + " contact — promote a replacement first.")
  public ResponseEntity<Void> unlinkAccount(
      @PathVariable UUID organizationId, @PathVariable UUID userId) {
    organizationAccountService.unlinkAccount(organizationId, userId);
    return ResponseEntity.noContent().build();
  }
}
