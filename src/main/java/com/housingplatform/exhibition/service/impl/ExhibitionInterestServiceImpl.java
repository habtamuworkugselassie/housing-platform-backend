package com.housingplatform.exhibition.service.impl;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.dto.ExhibitionInterestRequest;
import com.housingplatform.exhibition.dto.ExhibitionInterestResponse;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.exhibition.service.ExhibitionInterestService;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.SponsorshipRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.OrganizationPrimaryUserProvisioningService;
import com.housingplatform.identity.service.SponsorshipService;
import com.housingplatform.shared.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExhibitionInterestServiceImpl implements ExhibitionInterestService {

  private final ExhibitionInterestRepository repository;
  private final OrganizationRepository organizationRepository;
  private final SponsorshipRepository sponsorshipRepository;
  private final SponsorshipService sponsorshipService;
  private final UserRepository userRepository;
  private final OrganizationPrimaryUserProvisioningService primaryUserProvisioningService;

  @Override
  @Transactional
  public ExhibitionInterestResponse register(ExhibitionInterestRequest request) {
    String interestType = request.getInterestType().trim().toLowerCase();
    Sponsorship sponsorship =
        resolveSponsorshipForInterest(interestType, request.getSponsorshipId());

    String email = request.getEmail().trim().toLowerCase();
    userRepository
        .findByEmail(email)
        .ifPresent(
            u -> {
              if (u.getOrganization() != null) {
                throw new BusinessException(
                    "An account with this email already exists. Please sign in or use a different email to register.");
              }
            });
    String company = request.getCompany() != null ? request.getCompany().trim() : null;
    String phoneNumber = request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null;
    String orgName = (company != null && !company.isEmpty()) ? company : ("Exhibition: " + email);

    Organization organization =
        Organization.builder()
            .name(orgName)
            .type(Organization.OrganizationType.fromValue(request.getOrganizationType()))
            .status(Organization.OrganizationStatus.PENDING_APPROVAL)
            .description(
                request.getMessage() != null && !request.getMessage().trim().isEmpty()
                    ? request.getMessage().trim()
                    : null)
            .build();
    OrganizationContact contact =
        OrganizationContact.builder().organization(organization).email(email).build();
    organization.setContact(contact);
    contact
        .getPhones()
        .add(
            OrganizationPhone.builder()
                .contact(contact)
                .countryCode("+251")
                .number(phoneNumber != null ? phoneNumber : "")
                .displayOrder(0)
                .build());
    organization = organizationRepository.save(organization);

    primaryUserProvisioningService.linkExhibitionLeadUser(organization, email, phoneNumber);

    ExhibitionInterest entity =
        ExhibitionInterest.builder()
            .email(email)
            .phoneNumber(phoneNumber)
            .interestType(interestType)
            .company(company)
            .message(request.getMessage() != null ? request.getMessage().trim() : null)
            .organization(organization)
            .sponsorship(sponsorship)
            .build();
    entity = repository.save(entity);

    UUID sponsorshipIdForPendingApplication = sponsorship != null ? sponsorship.getId() : null;

    if (sponsorshipIdForPendingApplication != null) {
      organizationRepository.flush();
      sponsorshipService.createPendingApplicationForExhibitionInterest(
          organization.getId(),
          sponsorshipIdForPendingApplication,
          request.getMessage(),
          interestType);
    }

    return toResponse(entity);
  }

  private Sponsorship resolveSponsorshipForInterest(String interestType, UUID sponsorshipId) {
    if (!"exhibitor".equals(interestType)) {
      if (sponsorshipId != null) {
        throw new BusinessException(
            "Sponsorship package may only be set when registering as an exhibitor");
      }
      return null;
    }
    if (sponsorshipId == null) {
      throw new BusinessException("Please select a sponsorship package you are interested in");
    }
    Sponsorship s =
        sponsorshipRepository
            .findById(sponsorshipId)
            .orElseThrow(() -> new BusinessException("Unknown sponsorship package"));
    if (s.getStatus() != Sponsorship.SponsorshipStatus.ACTIVE) {
      throw new BusinessException("That sponsorship package is not available for selection");
    }
    return s;
  }

  private static ExhibitionInterestResponse toResponse(ExhibitionInterest e) {
    java.util.UUID sid = e.getSponsorship() != null ? e.getSponsorship().getId() : null;
    String sname = e.getSponsorship() != null ? e.getSponsorship().getName() : null;
    return ExhibitionInterestResponse.builder()
        .id(e.getId())
        .email(e.getEmail())
        .phoneNumber(e.getPhoneNumber())
        .interestType(e.getInterestType())
        .company(e.getCompany())
        .message(e.getMessage())
        .organizationId(e.getOrganization() != null ? e.getOrganization().getId() : null)
        .sponsorshipId(sid)
        .sponsorshipPackageName(sname)
        .build();
  }
}
