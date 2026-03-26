package com.housingplatform.identity.service;

import com.housingplatform.identity.dto.AdminOrganizationCreateRequest;
import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface OrganizationService {
  OrganizationResponse createOrganization(OrganizationRequest request);

  /** Create organization as admin; allows setting initial status (e.g. APPROVED). */
  OrganizationResponse createOrganizationAsAdmin(AdminOrganizationCreateRequest request);

  OrganizationResponse getOrganizationById(UUID id);

  /**
   * Load organization without marketplace visibility checks (e.g. internal enrichment). Prefer {@link
   * #getOrganizationById(UUID)} for API responses.
   */
  OrganizationResponse getOrganizationByIdUnrestricted(UUID id);

  /** Public lookup by registration number (e.g. footer base org). Empty if not found. */
  Optional<OrganizationResponse> getOrganizationByRegistrationNumber(String registrationNumber);

  /** Evict caches that depend on organization visibility or listing (properties-by-org, sponsorship). */
  void evictListingCachesForOrganization(UUID organizationId);

  OrganizationResponse getMyOrganization();

  OrganizationResponse getMyBank();

  List<OrganizationResponse> getAllOrganizations(String type, String status, String search);

  /**
   * List approved organizations by one or more types. Public, for marketplace listing.
   *
   * @param types comma-separated organization types (e.g. "BANK" or "CONSULTANT_ARCHITECT")
   */
  List<OrganizationResponse> getApprovedOrganizationsForMarketplace(String types);

  OrganizationResponse updateOrganization(UUID id, OrganizationRequest request);

  OrganizationResponse approveOrganization(UUID id);

  OrganizationResponse rejectOrganization(UUID id, String reason);

  OrganizationResponse suspendOrganization(UUID id, String reason);

  /**
   * Re-activate a suspended organization and its cancelled sponsorship applications (admin only).
   */
  OrganizationResponse reactivateOrganization(UUID id);

  OrganizationResponse getMySupplier();

  OrganizationResponse uploadOrganizationMedia(
      UUID organizationId, List<MultipartFile> files, String mediaKind);

  ResponseEntity<byte[]> getOrganizationMediaFile(UUID organizationId, UUID attachmentId);

  OrganizationResponse deleteOrganizationMedia(UUID organizationId, UUID attachmentId);

  /**
   * Upload an organization document (e.g. business registration, license, VAT, TIN). Replaces
   * existing document if present. Allowed types: BUSINESS_REGISTRATION, LICENSE, VAT_REGISTRATION,
   * TIN_REGISTRATION.
   */
  OrganizationResponse uploadOrganizationDocument(
      UUID organizationId, String documentType, MultipartFile file);
}
