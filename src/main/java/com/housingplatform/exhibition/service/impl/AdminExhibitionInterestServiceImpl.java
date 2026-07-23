package com.housingplatform.exhibition.service.impl;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.dto.AdminExhibitionInterestResponse;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.exhibition.service.AdminExhibitionInterestService;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.ProvisionOrganizationPrimaryUserRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.service.OrganizationPrimaryUserProvisioningService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminExhibitionInterestServiceImpl implements AdminExhibitionInterestService {

  private final ExhibitionInterestRepository repository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationPrimaryUserProvisioningService primaryUserProvisioningService;

  @Override
  @Transactional(readOnly = true)
  public Page<AdminExhibitionInterestResponse> list(Pageable pageable) {
    return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
  }

  private AdminExhibitionInterestResponse toResponse(ExhibitionInterest e) {
    Organization o = e.getOrganization();
    Sponsorship s = e.getSponsorship();
    UUID primaryId = null;
    if (o != null && o.getPrimaryContact() != null) {
      primaryId = o.getPrimaryContact().getId();
    }
    return AdminExhibitionInterestResponse.builder()
        .id(e.getId())
        .createdAt(e.getCreatedAt())
        .email(e.getEmail())
        .phoneNumber(e.getPhoneNumber())
        .interestType(e.getInterestType())
        .partnerRole(e.getPartnerRole() != null ? e.getPartnerRole().name() : null)
        .visibilityScope(e.getVisibilityScope() != null ? e.getVisibilityScope().name() : null)
        .contributionMode(e.getContributionMode() != null ? e.getContributionMode().name() : null)
        .company(e.getCompany())
        .message(e.getMessage())
        .organizationId(o != null ? o.getId() : null)
        .organizationName(o != null ? o.getName() : null)
        .organizationType(o != null && o.getType() != null ? o.getType().name() : null)
        .organizationStatus(o != null && o.getStatus() != null ? o.getStatus().name() : null)
        .sponsorshipId(s != null ? s.getId() : null)
        .sponsorshipPackageName(s != null ? s.getName() : null)
        .contactVerifiedAt(e.getContactVerifiedAt())
        .primaryContactUserId(primaryId)
        .build();
  }

  @Override
  @Transactional
  public AdminExhibitionInterestResponse verifyContact(
      UUID interestId, ProvisionOrganizationPrimaryUserRequest request) {
    ExhibitionInterest e =
        repository
            .findById(interestId)
            .orElseThrow(() -> new ResourceNotFoundException("ExhibitionInterest", interestId));
    final UUID organizationId = e.getOrganization() != null ? e.getOrganization().getId() : null;
    Organization org =
        organizationId != null
            ? organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId))
            : null;
    if (org == null) {
      throw new BusinessException("Exhibition interest has no linked organization");
    }

    if (org.getPrimaryContact() != null) {
      User pc = org.getPrimaryContact();
      if (pc.getStatus() == User.UserStatus.PENDING_VERIFICATION) {
        primaryUserProvisioningService.completePendingPrimaryContact(pc, request);
      }
      e.setContactVerifiedAt(LocalDateTime.now());
      e = repository.save(e);
      AdminExhibitionInterestResponse out = toResponse(e);
      out.setPrimaryContactUserId(pc.getId());
      return out;
    }

    User user =
        primaryUserProvisioningService.provisionPrimaryContactIfMissing(
            org, request, e.getEmail(), e.getPhoneNumber());

    e.setContactVerifiedAt(LocalDateTime.now());
    e = repository.save(e);
    AdminExhibitionInterestResponse out = toResponse(e);
    out.setPrimaryContactUserId(user.getId());
    return out;
  }
}
