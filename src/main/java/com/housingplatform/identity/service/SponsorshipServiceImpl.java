package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.domain.SponsorshipApplication;
import com.housingplatform.identity.dto.*;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.SponsorshipApplicationRepository;
import com.housingplatform.identity.repository.SponsorshipRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SponsorshipServiceImpl implements SponsorshipService {

  private final SponsorshipRepository sponsorshipRepository;
  private final SponsorshipApplicationRepository applicationRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationService organizationService;

  // ========== Sponsorship Package Management (Admin Only) ==========

  @Override
  @CacheEvict(
      value = {"activeSponsorships", "sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public SponsorshipResponse createSponsorship(CreateSponsorshipRequest request) {
    // Check if name already exists
    if (sponsorshipRepository.findByName(request.getName()).isPresent()) {
      throw new BusinessException("Sponsorship with this name already exists");
    }

    Sponsorship sponsorship =
        Sponsorship.builder()
            .name(request.getName())
            .description(request.getDescription())
            .type(request.getType())
            .basePrice(request.getBasePrice())
            .features(request.getFeatures())
            .status(Sponsorship.SponsorshipStatus.ACTIVE)
            .notes(request.getNotes())
            .build();

    Sponsorship saved = sponsorshipRepository.save(sponsorship);
    return toSponsorshipResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public SponsorshipResponse getSponsorshipById(UUID id) {
    Sponsorship sponsorship =
        sponsorshipRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sponsorship", id));
    return toSponsorshipResponse(sponsorship);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SponsorshipResponse> getAllSponsorships() {
    return sponsorshipRepository.findAll().stream()
        .map(this::toSponsorshipResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "activeSponsorships")
  public List<SponsorshipResponse> getActiveSponsorships() {
    List<Sponsorship> sponsorships =
        sponsorshipRepository.findByStatus(Sponsorship.SponsorshipStatus.ACTIVE);
    return sponsorships.stream().map(this::toSponsorshipResponse).collect(Collectors.toList());
  }

  @Override
  @CacheEvict(
      value = {"activeSponsorships", "sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public SponsorshipResponse updateSponsorship(UUID id, UpdateSponsorshipRequest request) {
    Sponsorship sponsorship =
        sponsorshipRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sponsorship", id));

    if (request.getName() != null && !request.getName().equals(sponsorship.getName())) {
      if (sponsorshipRepository.findByName(request.getName()).isPresent()) {
        throw new BusinessException("Sponsorship with this name already exists");
      }
      sponsorship.setName(request.getName());
    }

    if (request.getDescription() != null) {
      sponsorship.setDescription(request.getDescription());
    }

    if (request.getType() != null) {
      sponsorship.setType(request.getType());
    }

    if (request.getBasePrice() != null) {
      sponsorship.setBasePrice(request.getBasePrice());
    }

    if (request.getFeatures() != null) {
      sponsorship.setFeatures(request.getFeatures());
    }

    if (request.getStatus() != null) {
      sponsorship.setStatus(request.getStatus());
    }

    if (request.getNotes() != null) {
      sponsorship.setNotes(request.getNotes());
    }

    Sponsorship updated = sponsorshipRepository.save(sponsorship);
    return toSponsorshipResponse(updated);
  }

  @Override
  @CacheEvict(
      value = {"activeSponsorships", "sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public void deleteSponsorship(UUID id) {
    if (!sponsorshipRepository.existsById(id)) {
      throw new ResourceNotFoundException("Sponsorship", id);
    }
    sponsorshipRepository.deleteById(id);
  }

  // ========== Sponsorship Application Management ==========

  @Override
  public SponsorshipApplicationResponse applyForSponsorship(
      UUID organizationId, SponsorshipApplicationRequest request) {
    // Validate organization exists
    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

    // Validate sponsorship exists
    Sponsorship sponsorship =
        sponsorshipRepository
            .findById(request.getSponsorshipId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Sponsorship", request.getSponsorshipId()));

    // Check if sponsorship is active
    if (sponsorship.getStatus() != Sponsorship.SponsorshipStatus.ACTIVE) {
      throw new BusinessException("Cannot apply for inactive sponsorship");
    }

    // Validate dates
    if (request.getEndDate().isBefore(request.getStartDate())) {
      throw new BusinessException("End date must be after start date");
    }

    LocalDateTime now = LocalDateTime.now();
    if (request.getEndDate().isBefore(now)) {
      throw new BusinessException("End date must be in the future");
    }

    // Check for overlapping approved applications
    List<SponsorshipApplication> existingApplications =
        applicationRepository.findBySponsorshipIdAndOrganizationIdAndStatus(
            request.getSponsorshipId(),
            organizationId,
            SponsorshipApplication.ApplicationStatus.APPROVED);
    for (SponsorshipApplication existing : existingApplications) {
      if (existing.getStatus() == SponsorshipApplication.ApplicationStatus.APPROVED) {
        boolean overlaps =
            !(request.getEndDate().isBefore(existing.getStartDate())
                || request.getStartDate().isAfter(existing.getEndDate()));
        if (overlaps) {
          throw new BusinessException(
              "An approved sponsorship application already exists for this date range");
        }
      }
    }

    // Create application
    SponsorshipApplication application =
        SponsorshipApplication.builder()
            .sponsorship(sponsorship)
            .organization(organization)
            .status(SponsorshipApplication.ApplicationStatus.PENDING)
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .notes(request.getNotes())
            .amount(request.getAmount())
            .paymentReference(request.getPaymentReference())
            .build();

    SponsorshipApplication saved = applicationRepository.save(application);
    return toApplicationResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public SponsorshipApplicationResponse getApplicationById(UUID id) {
    SponsorshipApplication application =
        applicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SponsorshipApplication", id));
    return toApplicationResponse(application);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SponsorshipApplicationResponse> getApplicationsByOrganization(UUID organizationId) {
    return applicationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(this::toApplicationResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SponsorshipApplicationResponse> getAllApplications() {
    return applicationRepository.findAll().stream()
        .map(this::toApplicationResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SponsorshipApplicationResponse> getPendingApplications() {
    return applicationRepository
        .findByStatus(SponsorshipApplication.ApplicationStatus.PENDING)
        .stream()
        .map(this::toApplicationResponse)
        .collect(Collectors.toList());
  }

  @Override
  @CacheEvict(
      value = {"sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public SponsorshipApplicationResponse approveApplication(UUID id, String notes) {
    SponsorshipApplication application =
        applicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SponsorshipApplication", id));

    if (application.getStatus() != SponsorshipApplication.ApplicationStatus.PENDING) {
      throw new BusinessException("Only pending applications can be approved");
    }

    // Cancel any other approved applications that overlap
    List<SponsorshipApplication> overlapping =
        applicationRepository.findByOrganizationId(application.getOrganization().getId()).stream()
            .filter(
                app ->
                    app.getStatus() == SponsorshipApplication.ApplicationStatus.APPROVED
                        && app.getId() != application.getId())
            .filter(
                app -> {
                  boolean overlaps =
                      !(application.getEndDate().isBefore(app.getStartDate())
                          || application.getStartDate().isAfter(app.getEndDate()));
                  return overlaps;
                })
            .collect(Collectors.toList());

    for (SponsorshipApplication overlap : overlapping) {
      overlap.setStatus(SponsorshipApplication.ApplicationStatus.CANCELLED);
      applicationRepository.save(overlap);
    }

    application.setStatus(SponsorshipApplication.ApplicationStatus.APPROVED);
    if (notes != null) {
      application.setNotes(notes);
    }

    SponsorshipApplication saved = applicationRepository.save(application);
    return toApplicationResponse(saved);
  }

  @Override
  public SponsorshipApplicationResponse rejectApplication(UUID id, String reason) {
    SponsorshipApplication application =
        applicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SponsorshipApplication", id));

    if (application.getStatus() != SponsorshipApplication.ApplicationStatus.PENDING) {
      throw new BusinessException("Only pending applications can be rejected");
    }

    application.setStatus(SponsorshipApplication.ApplicationStatus.REJECTED);
    application.setRejectionReason(reason);

    SponsorshipApplication saved = applicationRepository.save(application);
    return toApplicationResponse(saved);
  }

  @Override
  @CacheEvict(
      value = {"sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public void cancelApplication(UUID id) {
    SponsorshipApplication application =
        applicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SponsorshipApplication", id));

    // Admin can cancel any; realtor can cancel only their organization's
    // application
    if (!UserContext.isAdmin()) {
      UUID userOrgId =
          UserContext.getCurrentUserOrganizationId()
              .orElseThrow(
                  () ->
                      new BusinessException("Organization context required to cancel sponsorship"));
      if (application.getOrganization() == null
          || !application.getOrganization().getId().equals(userOrgId)) {
        throw new BusinessException(
            "You can only cancel your organization's sponsorship application");
      }
    }

    if (application.getStatus() == SponsorshipApplication.ApplicationStatus.APPROVED) {
      application.setStatus(SponsorshipApplication.ApplicationStatus.CANCELLED);
      applicationRepository.save(application);
    } else {
      throw new BusinessException("Only approved applications can be cancelled");
    }
  }

  @Override
  @CacheEvict(
      value = {"sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public SponsorshipApplicationResponse assignOrganizationToSponsorship(
      AdminAssignSponsorshipRequest request) {
    java.time.LocalDateTime startDateTime = request.getStartDate().atStartOfDay();
    java.time.LocalDateTime endDateTime = request.getEndDate().atTime(23, 59, 59);
    SponsorshipApplicationRequest appRequest =
        SponsorshipApplicationRequest.builder()
            .sponsorshipId(request.getSponsorshipId())
            .startDate(startDateTime)
            .endDate(endDateTime)
            .notes(request.getNotes())
            .amount(request.getAmount())
            .paymentReference(request.getPaymentReference())
            .build();
    SponsorshipApplicationResponse application =
        applyForSponsorship(request.getOrganizationId(), appRequest);
    if (Boolean.TRUE.equals(request.getAutoApprove())) {
      application = approveApplication(application.getId(), request.getNotes());
    }
    return application;
  }

  // ========== Helper Methods ==========

  private SponsorshipResponse toSponsorshipResponse(Sponsorship sponsorship) {
    return SponsorshipResponse.builder()
        .id(sponsorship.getId())
        .name(sponsorship.getName())
        .description(sponsorship.getDescription())
        .type(sponsorship.getType())
        .basePrice(sponsorship.getBasePrice())
        .features(sponsorship.getFeatures())
        .status(sponsorship.getStatus())
        .notes(sponsorship.getNotes())
        .createdAt(sponsorship.getCreatedAt())
        .updatedAt(sponsorship.getUpdatedAt())
        .build();
  }

  private SponsorshipApplicationResponse toApplicationResponse(SponsorshipApplication application) {
    return SponsorshipApplicationResponse.builder()
        .id(application.getId())
        .sponsorshipId(application.getSponsorship().getId())
        .sponsorshipName(application.getSponsorship().getName())
        .sponsorship(toSponsorshipResponse(application.getSponsorship()))
        .organizationId(application.getOrganization().getId())
        .organizationName(application.getOrganization().getName())
        .status(application.getStatus())
        .startDate(application.getStartDate())
        .endDate(application.getEndDate())
        .isActive(application.isActive())
        .notes(application.getNotes())
        .rejectionReason(application.getRejectionReason())
        .amount(application.getAmount())
        .paymentReference(application.getPaymentReference())
        .createdAt(application.getCreatedAt())
        .updatedAt(application.getUpdatedAt())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "sponsoredOrganizations")
  public List<SponsoredOrganizationResponse> getActiveSponsoredOrganizations() {
    LocalDateTime now = LocalDateTime.now();
    List<SponsorshipApplication> applications =
        applicationRepository.findAllActiveApplications(now);
    return applications.stream()
        .map(
            app -> {
              // Exclude suspended organizations (check entity so they never appear on
              // carousel)
              Organization orgEntity = app.getOrganization();
              if (orgEntity == null
                  || orgEntity.getStatus() == Organization.OrganizationStatus.SUSPENDED) {
                return null;
              }
              OrganizationResponse org = organizationService.getOrganizationById(orgEntity.getId());
              String videoUrl = null;
              String splashImageUrl = null;
              if (org.getMedia() != null) {
                videoUrl =
                    org.getMedia().stream()
                        .filter(m -> "VIDEO".equals(m.getMediaKind()))
                        .map(OrganizationMediaItem::getUrl)
                        .findFirst()
                        .orElse(null);
                splashImageUrl =
                    org.getMedia().stream()
                        .filter(m -> "IMAGE".equals(m.getMediaKind()))
                        .map(OrganizationMediaItem::getUrl)
                        .findFirst()
                        .orElse(null);
              }
              return SponsoredOrganizationResponse.builder()
                  .id(org.getId())
                  .name(org.getName())
                  .logoUrl(org.getLogoUrl())
                  .videoUrl(videoUrl)
                  .splashImageUrl(splashImageUrl)
                  .address(org.getAddress())
                  .city(org.getCity())
                  .country(org.getCountry())
                  .sponsorshipType(app.getSponsorship().getType().name())
                  .basePrice(app.getSponsorship().getBasePrice())
                  .organizationType(
                      org.getType() != null ? org.getType().name() : null)
                  .build();
            })
        .filter(r -> r != null)
        .sorted(
            (a, b) -> {
              java.math.BigDecimal pa =
                  a.getBasePrice() != null ? a.getBasePrice() : java.math.BigDecimal.ZERO;
              java.math.BigDecimal pb =
                  b.getBasePrice() != null ? b.getBasePrice() : java.math.BigDecimal.ZERO;
              return pb.compareTo(pa);
            })
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "exclusiveOrganizations")
  public List<SponsoredOrganizationResponse> getExclusiveOrganizations() {
    return getActiveSponsoredOrganizations().stream()
        .filter(
            r ->
                r.getSponsorshipType() != null
                    && "EXCLUSIVE".equals(r.getSponsorshipType().toUpperCase()))
        .collect(Collectors.toList());
  }
}
