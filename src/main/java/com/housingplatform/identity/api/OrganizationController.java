package com.housingplatform.identity.api;

import com.housingplatform.identity.dto.OrganizationDocumentReviewsPatchRequest;
import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.dto.RejectOrganizationRequest;
import com.housingplatform.identity.dto.SupplierSubcategoryIdsRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

  @GetMapping("/marketplace")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "List approved organizations for marketplace",
      description =
          "Public. Returns approved organizations by type(s). Query param: type (e.g. BANK or"
              + " CONSULTANT_ARCHITECT). Optional subcategoryId filters SUPPLIER orgs.")
  public ResponseEntity<List<OrganizationResponse>> getMarketplaceOrganizations(
      @RequestParam String type, @RequestParam(required = false) UUID subcategoryId) {
    List<OrganizationResponse> list =
        organizationService.getApprovedOrganizationsForMarketplace(type, subcategoryId);
    return ResponseEntity.ok(list);
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

  @PatchMapping("/{id}/supplier-subcategories")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(
      summary = "Update supplier material subcategories",
      description =
          "Replaces linked subcategories for a SUPPLIER organization. Admin or supplier primary"
              + " contact.")
  public ResponseEntity<OrganizationResponse> patchSupplierSubcategories(
      @PathVariable UUID id, @Valid @RequestBody SupplierSubcategoryIdsRequest body) {
    return ResponseEntity.ok(organizationService.updateOrganizationSupplierSubcategories(id, body));
  }

  @PatchMapping("/{id}/document-reviews")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @AuthActionScope("organizations.approve")
  @Operation(
      summary = "Update organization document reviews",
      description =
          "Set admin review status and/or comments for business registration, license, VAT, and TIN"
              + " documents. Only fields present in the body are updated. Admin only.")
  public ResponseEntity<OrganizationResponse> patchOrganizationDocumentReviews(
      @PathVariable UUID id, @RequestBody OrganizationDocumentReviewsPatchRequest body) {
    OrganizationResponse updated = organizationService.patchOrganizationDocumentReviews(id, body);
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
      @PathVariable UUID id, @RequestBody(required = false) RejectOrganizationRequest body) {
    String reason = body != null ? body.getReason() : null;
    OrganizationResponse rejected = organizationService.rejectOrganization(id, reason);
    return ResponseEntity.ok(rejected);
  }

  @PutMapping("/{id}/suspend")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @AuthActionScope("organizations.suspend")
  @Operation(summary = "Suspend organization", description = "Suspend an organization (admin only)")
  public ResponseEntity<OrganizationResponse> suspendOrganization(
      @PathVariable UUID id, @RequestBody(required = false) RejectOrganizationRequest body) {
    String reason = body != null ? body.getReason() : null;
    OrganizationResponse suspended = organizationService.suspendOrganization(id, reason);
    return ResponseEntity.ok(suspended);
  }

  @PutMapping("/{id}/reactivate")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @AuthActionScope("organizations.reactivate")
  @Operation(
      summary = "Reactivate organization",
      description =
          "Re-activate a suspended organization and its cancelled sponsorship applications (admin only).")
  public ResponseEntity<OrganizationResponse> reactivateOrganization(@PathVariable UUID id) {
    OrganizationResponse reactivated = organizationService.reactivateOrganization(id);
    return ResponseEntity.ok(reactivated);
  }

  @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @AuthActionScope("organizations.update")
  @Operation(
      summary = "Upload organization media (logo, images, videos)",
      description =
          "Upload logo (mediaKind=LOGO), images, or videos for an organization. Admin or organization primary contact.")
  public ResponseEntity<OrganizationResponse> uploadOrganizationMedia(
      @PathVariable UUID id,
      @RequestParam(value = "files", required = false) List<MultipartFile> files,
      @RequestParam(required = false, defaultValue = "IMAGE") String mediaKind) {
    OrganizationResponse updated =
        organizationService.uploadOrganizationMedia(id, files, mediaKind);
    return ResponseEntity.ok(updated);
  }

  @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @AuthActionScope("organizations.update")
  @Operation(
      summary = "Upload organization document",
      description =
          "Upload a document for business registration, license, VAT registration, or TIN registration. documentType: BUSINESS_REGISTRATION, LICENSE, VAT_REGISTRATION, TIN_REGISTRATION. Admin or organization primary contact.")
  public ResponseEntity<OrganizationResponse> uploadOrganizationDocument(
      @PathVariable UUID id,
      @RequestParam String documentType,
      @RequestParam("file") MultipartFile file) {
    OrganizationResponse updated =
        organizationService.uploadOrganizationDocument(id, documentType, file);
    return ResponseEntity.ok(updated);
  }

  @GetMapping("/{id}/media/{attachmentId}/file")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get organization media file",
      description = "Retrieve logo, image or video file for an organization. Public access.")
  public ResponseEntity<byte[]> getOrganizationMediaFile(
      @PathVariable UUID id, @PathVariable UUID attachmentId) {
    return organizationService.getOrganizationMediaFile(id, attachmentId);
  }

  @DeleteMapping("/{id}/media/{attachmentId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @AuthActionScope("organizations.update")
  @Operation(
      summary = "Delete organization media",
      description = "Delete a media attachment from an organization. Admin or primary contact.")
  public ResponseEntity<OrganizationResponse> deleteOrganizationMedia(
      @PathVariable UUID id, @PathVariable UUID attachmentId) {
    OrganizationResponse updated = organizationService.deleteOrganizationMedia(id, attachmentId);
    return ResponseEntity.ok(updated);
  }
}
