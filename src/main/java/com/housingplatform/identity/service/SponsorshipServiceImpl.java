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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SponsorshipServiceImpl implements SponsorshipService {

  private final SponsorshipRepository sponsorshipRepository;
  private final SponsorshipApplicationRepository applicationRepository;
  private final OrganizationRepository organizationRepository;

  // ========== Sponsorship Package Management (Admin Only) ==========

  @Override
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
  public List<SponsorshipResponse> getActiveSponsorships() {
    return sponsorshipRepository.findByStatus(Sponsorship.SponsorshipStatus.ACTIVE).stream()
        .map(this::toSponsorshipResponse)
        .collect(Collectors.toList());
  }

  @Override
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
        applicationRepository.findByOrganizationId(organizationId);
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
  public void cancelApplication(UUID id) {
    SponsorshipApplication application =
        applicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SponsorshipApplication", id));

    if (application.getStatus() == SponsorshipApplication.ApplicationStatus.APPROVED) {
      application.setStatus(SponsorshipApplication.ApplicationStatus.CANCELLED);
      applicationRepository.save(application);
    } else {
      throw new BusinessException("Only approved applications can be cancelled");
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
}
