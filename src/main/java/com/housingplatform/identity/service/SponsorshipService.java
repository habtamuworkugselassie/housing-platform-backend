package com.housingplatform.identity.service;

import com.housingplatform.identity.dto.*;

import java.util.List;
import java.util.UUID;

public interface SponsorshipService {
    // Sponsorship package management (admin only)
    SponsorshipResponse createSponsorship(CreateSponsorshipRequest request);
    SponsorshipResponse getSponsorshipById(UUID id);
    List<SponsorshipResponse> getAllSponsorships();
    List<SponsorshipResponse> getActiveSponsorships();
    SponsorshipResponse updateSponsorship(UUID id, UpdateSponsorshipRequest request);
    void deleteSponsorship(UUID id);
    
    // Sponsorship application management
    SponsorshipApplicationResponse applyForSponsorship(UUID organizationId, SponsorshipApplicationRequest request);
    SponsorshipApplicationResponse getApplicationById(UUID id);
    List<SponsorshipApplicationResponse> getApplicationsByOrganization(UUID organizationId);
    List<SponsorshipApplicationResponse> getAllApplications();
    List<SponsorshipApplicationResponse> getPendingApplications();
    SponsorshipApplicationResponse approveApplication(UUID id, String notes);
    SponsorshipApplicationResponse rejectApplication(UUID id, String reason);
    void cancelApplication(UUID id);
}
