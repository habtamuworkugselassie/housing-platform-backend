package com.housingplatform.identity.service;

import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import java.util.List;
import java.util.UUID;

public interface OrganizationService {
  OrganizationResponse createOrganization(OrganizationRequest request);

  OrganizationResponse getOrganizationById(UUID id);

  OrganizationResponse getMyOrganization();

  OrganizationResponse getMyBank();

  List<OrganizationResponse> getAllOrganizations(String type, String status, String search);

  OrganizationResponse updateOrganization(UUID id, OrganizationRequest request);

  OrganizationResponse approveOrganization(UUID id);

  OrganizationResponse rejectOrganization(UUID id, String reason);

  OrganizationResponse suspendOrganization(UUID id, String reason);

  OrganizationResponse getMySupplier();
}
