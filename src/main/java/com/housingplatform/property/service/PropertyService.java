package com.housingplatform.property.service;

import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.PropertyRequest;
import com.housingplatform.property.dto.PropertyResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface PropertyService {
  PropertyResponse createProperty(PropertyRequest request, UUID agentId);

  PropertyResponse getPropertyById(UUID id);

  Page<PropertyResponse> getAllProperties(
      String status, String city, String type, Pageable pageable, boolean publicOnly);

  PropertyResponse updateProperty(UUID id, PropertyRequest request, UUID agentId);

  void deleteProperty(UUID id, UUID agentId);

  List<PropertyResponse> getPropertiesByCompanyId(UUID companyId);

  /**
   * Every property belonging to the calling realtor's own organization.
   *
   * <p>Takes no id: the organization is resolved from the authenticated user's agent record, so a
   * realtor cannot read another company's listings by guessing an id. Works for every agent, not
   * just super agents — managing properties is already company-scoped
   * (RealEstateAgentServiceImpl#validateAgentCanManageProperty), so what an agent may edit and what
   * they can see now agree.
   */
  List<PropertyResponse> getMyOrganizationProperties();

  /**
   * Throws unless the caller may read the given organization's properties: an admin, or an agent of
   * that organization. Guards the by-id endpoint, whose scope check alone proves only that the
   * caller is some realtor.
   */
  void assertCallerBelongsToOrganization(UUID organizationId);

  /** Public marketplace: available properties for an organization with sponsorship enrichment. */
  List<PropertyResponse> getAvailablePropertiesByCompanyIdForMarketplace(UUID companyId);

  List<PropertyResponse> getPropertiesByAgentId(UUID agentId);

  /**
   * Maps properties to responses with images and sponsorship/verification enrichment. Used by
   * building units and any list that needs realEstateCompanyVerified and company info.
   */
  List<PropertyResponse> toResponseWithImagesAndSponsorship(List<Property> properties);

  List<PropertyResponse> searchProperties(
      String companyName, String city, String state, String country, String title, Integer limit);

  PropertyResponse uploadPropertyMedia(
      UUID id, List<MultipartFile> files, List<String> captions, UUID agentId);

  PropertyResponse deletePropertyImage(UUID id, UUID imageId, UUID agentId);

  org.springframework.http.ResponseEntity<byte[]> getPropertyImageFile(UUID id, UUID imageId);

  /**
   * First image and video URLs from any of the organization's properties. Public, for sponsor
   * carousel fallback when the organization has no logo/video.
   */
  com.housingplatform.property.dto.FirstPropertyMediaResponse getFirstPropertyMediaForOrganization(
      UUID organizationId);
}
