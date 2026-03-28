package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.domain.SponsorshipApplication;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.*;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.SponsorshipApplicationRepository;
import com.housingplatform.identity.repository.SponsorshipRepository;
import com.housingplatform.identity.service.OrganizationPrimaryUserProvisioningService;
import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.identity.service.SponsorshipService;
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
  private final RealEstateAgentRepository realEstateAgentRepository;
  private final OrganizationService organizationService;
  private final OrganizationPrimaryUserProvisioningService
      organizationPrimaryUserProvisioningService;

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
  @CacheEvict(
      value = {"sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public SponsorshipApplicationResponse applyForSponsorship(
      UUID organizationId, SponsorshipApplicationRequest request) {
    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

    if (organization.getStatus() != Organization.OrganizationStatus.APPROVED) {
      throw new BusinessException(
          "Only approved organizations can apply for sponsorship; current status is "
              + organization.getStatus());
    }

    if (applicationRepository.existsByOrganization_IdAndStatus(
        organizationId, SponsorshipApplication.ApplicationStatus.PENDING)) {
      throw new BusinessException(
          "A pending sponsorship application already exists for this organization");
    }

    Sponsorship sponsorship =
        sponsorshipRepository
            .findById(request.getSponsorshipId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Sponsorship", request.getSponsorshipId()));

    if (sponsorship.getStatus() != Sponsorship.SponsorshipStatus.ACTIVE) {
      throw new BusinessException("Cannot apply for inactive sponsorship");
    }

    if (request.getEndDate().isBefore(request.getStartDate())) {
      throw new BusinessException("End date must be after start date");
    }

    LocalDateTime now = LocalDateTime.now();
    if (request.getEndDate().isBefore(now)) {
      throw new BusinessException("End date must be in the future");
    }

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
            .organizationWasApprovedBeforeApplication(true)
            .build();

    SponsorshipApplication saved = applicationRepository.save(application);

    organization.setStatus(Organization.OrganizationStatus.SPONSORSHIP_PENDING);
    organizationRepository.save(organization);
    organizationService.evictListingCachesForOrganization(organizationId);

    return toApplicationResponse(saved);
  }

  @Override
  @CacheEvict(
      value = {"sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public SponsorshipApplicationResponse createPendingApplicationForExhibitionInterest(
      UUID organizationId, UUID sponsorshipId, String registrantMessage) {
    if (applicationRepository.existsByOrganization_IdAndStatus(
        organizationId, SponsorshipApplication.ApplicationStatus.PENDING)) {
      return applicationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
          .filter(a -> a.getStatus() == SponsorshipApplication.ApplicationStatus.PENDING)
          .findFirst()
          .map(this::toApplicationResponse)
          .orElseThrow(
              () ->
                  new BusinessException(
                      "Pending sponsorship application expected but was not found for organization"));
    }

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

    Sponsorship sponsorship =
        sponsorshipRepository
            .findById(sponsorshipId)
            .orElseThrow(() -> new ResourceNotFoundException("Sponsorship", sponsorshipId));

    if (sponsorship.getStatus() != Sponsorship.SponsorshipStatus.ACTIVE) {
      throw new BusinessException("Cannot apply for inactive sponsorship");
    }

    LocalDateTime start = LocalDateTime.now();
    LocalDateTime end = start.plusYears(1);

    StringBuilder notes = new StringBuilder("Exhibition interest registration");
    if (registrantMessage != null && !registrantMessage.trim().isEmpty()) {
      String trimmed = registrantMessage.trim();
      if (trimmed.length() > 800) {
        trimmed = trimmed.substring(0, 800) + "…";
      }
      notes.append(": ").append(trimmed);
    }

    SponsorshipApplication application =
        SponsorshipApplication.builder()
            .sponsorship(sponsorship)
            .organization(organization)
            .status(SponsorshipApplication.ApplicationStatus.PENDING)
            .startDate(start)
            .endDate(end)
            .notes(notes.toString())
            .amount(sponsorship.getBasePrice())
            .organizationWasApprovedBeforeApplication(false)
            .build();

    SponsorshipApplication saved = applicationRepository.save(application);
    organization.setStatus(Organization.OrganizationStatus.SPONSORSHIP_PENDING);
    organizationRepository.save(organization);
    organizationService.evictListingCachesForOrganization(organizationId);
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
    return approveApplicationInternal(id, notes, true);
  }

  private SponsorshipApplicationResponse approveApplicationInternal(
      UUID id, String notes, boolean requireVerification) {
    SponsorshipApplication application =
        applicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SponsorshipApplication", id));

    if (application.getStatus() != SponsorshipApplication.ApplicationStatus.PENDING) {
      throw new BusinessException("Only pending applications can be approved");
    }

    if (requireVerification) {
      if (application.getOrganizationVerifiedAt() == null
          || application.getUserVerifiedAt() == null) {
        throw new BusinessException(
            "Organization and user must both be verified before approving this sponsorship");
      }
    }

    Organization org = application.getOrganization();
    org.setStatus(Organization.OrganizationStatus.APPROVED);
    organizationRepository.save(org);

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
    organizationService.evictListingCachesForOrganization(org.getId());
    return toApplicationResponse(saved);
  }

  @Override
  @CacheEvict(
      value = {"sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public SponsorshipApplicationResponse verifyOrganizationForApplication(UUID id) {
    SponsorshipApplication application =
        applicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SponsorshipApplication", id));
    if (application.getStatus() != SponsorshipApplication.ApplicationStatus.PENDING) {
      throw new BusinessException("Only pending applications can be verified");
    }
    application.setOrganizationVerifiedAt(LocalDateTime.now());
    SponsorshipApplication saved = applicationRepository.save(application);
    return toApplicationResponse(saved);
  }

  @Override
  @CacheEvict(
      value = {"sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
  public SponsorshipApplicationResponse verifyUserForApplication(
      UUID id, ProvisionOrganizationPrimaryUserRequest provisionRequest) {
    SponsorshipApplication application =
        applicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SponsorshipApplication", id));
    if (application.getStatus() != SponsorshipApplication.ApplicationStatus.PENDING) {
      throw new BusinessException("Only pending applications can be verified");
    }
    Organization org =
        organizationRepository
            .findById(application.getOrganization().getId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Organization", application.getOrganization().getId()));

    java.util.Optional<User> verificationSubject = resolveVerificationUser(org);
    if (verificationSubject.isPresent()) {
      User u = verificationSubject.get();
      if (u.getStatus() == User.UserStatus.PENDING_VERIFICATION) {
        if (provisionRequest == null) {
          throw new BusinessException(
              "This organization's primary user is pending activation. Include firstName, lastName, and password in the request body.");
        }
        organizationPrimaryUserProvisioningService.completePendingPrimaryContact(
            u, provisionRequest);
      }
      application.setUserVerifiedAt(LocalDateTime.now());
      SponsorshipApplication saved = applicationRepository.save(application);
      return toApplicationResponse(saved);
    }

    if (verificationSubjectFromContact(org) == null) {
      throw new BusinessException(
          "No primary contact, super agent, or organization contact email found for this organization");
    }

    if (provisionRequest == null) {
      throw new BusinessException(
          "Include firstName, lastName, and password in the request body to create the organization's primary user "
              + "(login uses the organization contact email from the registration).");
    }

    organizationPrimaryUserProvisioningService.provisionPrimaryContactIfMissing(
        org, provisionRequest, null, null);
    application.setUserVerifiedAt(LocalDateTime.now());
    SponsorshipApplication saved = applicationRepository.save(application);
    organizationService.evictListingCachesForOrganization(org.getId());
    return toApplicationResponse(saved);
  }

  @Override
  @CacheEvict(
      value = {"sponsoredOrganizations", "exclusiveOrganizations"},
      allEntries = true)
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
    application.setOrganizationVerifiedAt(null);
    application.setUserVerifiedAt(null);

    Organization org = application.getOrganization();
    restoreOrganizationStatusAfterPendingRemoval(org, application);
    organizationRepository.save(org);

    SponsorshipApplication saved = applicationRepository.save(application);
    organizationService.evictListingCachesForOrganization(org.getId());
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
      UUID orgId = application.getOrganization().getId();
      application.setStatus(SponsorshipApplication.ApplicationStatus.CANCELLED);
      applicationRepository.save(application);
      organizationService.evictListingCachesForOrganization(orgId);
    } else if (application.getStatus() == SponsorshipApplication.ApplicationStatus.PENDING) {
      application.setStatus(SponsorshipApplication.ApplicationStatus.CANCELLED);
      application.setOrganizationVerifiedAt(null);
      application.setUserVerifiedAt(null);
      Organization org = application.getOrganization();
      restoreOrganizationStatusAfterPendingRemoval(org, application);
      organizationRepository.save(org);
      applicationRepository.save(application);
      organizationService.evictListingCachesForOrganization(org.getId());
    } else {
      throw new BusinessException("Only approved or pending applications can be cancelled");
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
      application = approveApplicationInternal(application.getId(), request.getNotes(), false);
    }
    return application;
  }

  /** User to verify: primary contact when set; otherwise the organization's super agent. */
  private java.util.Optional<User> resolveVerificationUser(Organization organization) {
    if (organization.getPrimaryContact() != null) {
      return java.util.Optional.of(organization.getPrimaryContact());
    }
    return realEstateAgentRepository
        .findSuperAgentByOrganizationId(organization.getId())
        .map(RealEstateAgent::getUser);
  }

  /**
   * Exhibition (and similar) signups: registrant email/phone on {@link OrganizationContact} when no
   * platform user is linked yet.
   */
  private SponsorshipVerificationUserSummary verificationSubjectFromContact(
      Organization organization) {
    OrganizationContact contact = organization.getContact();
    if (contact == null) {
      return null;
    }
    String email = contact.getEmail();
    if (email == null || email.isBlank()) {
      return null;
    }
    String phone =
        contact.getPhones().stream()
            .findFirst()
            .map(OrganizationPhone::getNumber)
            .filter(n -> n != null && !n.isBlank())
            .orElse(null);
    return SponsorshipVerificationUserSummary.builder()
        .id(null)
        .email(email.trim())
        .firstName(null)
        .lastName(null)
        .phoneNumber(phone)
        .build();
  }

  private void restoreOrganizationStatusAfterPendingRemoval(
      Organization org, SponsorshipApplication application) {
    if (Boolean.FALSE.equals(application.getOrganizationWasApprovedBeforeApplication())) {
      org.setStatus(Organization.OrganizationStatus.PENDING_APPROVAL);
    } else {
      org.setStatus(Organization.OrganizationStatus.APPROVED);
    }
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
    Organization org = application.getOrganization();
    User verificationSubject = resolveVerificationUser(org).orElse(null);
    SponsorshipVerificationUserSummary verificationUser;
    if (verificationSubject != null) {
      verificationUser =
          SponsorshipVerificationUserSummary.builder()
              .id(verificationSubject.getId())
              .email(verificationSubject.getEmail())
              .firstName(verificationSubject.getFirstName())
              .lastName(verificationSubject.getLastName())
              .phoneNumber(verificationSubject.getPhoneNumber())
              .build();
    } else {
      verificationUser = verificationSubjectFromContact(org);
    }
    return SponsorshipApplicationResponse.builder()
        .id(application.getId())
        .sponsorshipId(application.getSponsorship().getId())
        .sponsorshipName(application.getSponsorship().getName())
        .sponsorship(toSponsorshipResponse(application.getSponsorship()))
        .organizationId(org.getId())
        .organizationName(org.getName())
        .organizationStatus(org.getStatus())
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
        .organizationVerifiedAt(application.getOrganizationVerifiedAt())
        .userVerifiedAt(application.getUserVerifiedAt())
        .verificationUser(verificationUser)
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
                  || orgEntity.getStatus() == Organization.OrganizationStatus.SUSPENDED
                  || orgEntity.getStatus() == Organization.OrganizationStatus.SPONSORSHIP_PENDING) {
                return null;
              }
              OrganizationResponse org =
                  organizationService.getOrganizationByIdUnrestricted(orgEntity.getId());
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
                  .organizationType(org.getType() != null ? org.getType().name() : null)
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
