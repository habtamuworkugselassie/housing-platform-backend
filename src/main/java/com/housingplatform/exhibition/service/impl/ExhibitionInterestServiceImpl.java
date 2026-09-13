package com.housingplatform.exhibition.service.impl;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.dto.ExhibitionInterestRequest;
import com.housingplatform.exhibition.dto.ExhibitionInterestResponse;
import com.housingplatform.exhibition.email.ExhibitionInterestRegisteredEvent;
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
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher events;

  @Override
  @Transactional
  public ExhibitionInterestResponse register(ExhibitionInterestRequest request) {
    String interestType = request.getInterestType().trim().toLowerCase();
    validatePartnerInterest(request, interestType);
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
            .partnerRole("partner".equals(interestType) ? request.getPartnerRole() : null)
            .visibilityScope("partner".equals(interestType) ? request.getVisibilityScope() : null)
            .contributionMode("partner".equals(interestType) ? request.getContributionMode() : null)
            .company(company)
            .message(request.getMessage() != null ? request.getMessage().trim() : null)
            .organization(organization)
            .sponsorship(sponsorship)
            .utmSource(clip(request.getUtmSource(), 120))
            .utmMedium(clip(request.getUtmMedium(), 120))
            .utmCampaign(clip(request.getUtmCampaign(), 180))
            .utmTerm(clip(request.getUtmTerm(), 180))
            .utmContent(clip(request.getUtmContent(), 180))
            .referrer(clip(request.getReferrer(), 500))
            .landingPath(clip(request.getLandingPath(), 500))
            .unsubscribeToken(newUnsubscribeToken())
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

    // Delivered after this transaction commits, so nobody is told "you are registered" for a row
    // that then rolled back. See ExhibitionLifecycleEmailListener.
    events.publishEvent(new ExhibitionInterestRegisteredEvent(entity.getId()));

    return toResponse(entity);
  }

  @Override
  @Transactional
  public boolean unsubscribe(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    return repository
        .findByUnsubscribeToken(token.trim())
        .map(
            interest -> {
              if (!interest.isUnsubscribed()) {
                interest.setUnsubscribedAt(java.time.LocalDateTime.now());
                repository.save(interest);
              }
              return true;
            })
        .orElse(false);
  }

  private Sponsorship resolveSponsorshipForInterest(String interestType, UUID sponsorshipId) {
    if ("visitor".equals(interestType)) {
      if (sponsorshipId != null) {
        throw new BusinessException(
            "Sponsorship package may only be set for exhibitor or partner interest");
      }
      return null;
    }
    if ("exhibitor".equals(interestType) && sponsorshipId == null) {
      throw new BusinessException("Please select a sponsorship package you are interested in");
    }
    if (sponsorshipId == null) {
      return null;
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

  private static void validatePartnerInterest(
      ExhibitionInterestRequest request, String interestType) {
    if (!"partner".equals(interestType)) {
      return;
    }
    if (request.getPartnerRole() == null) {
      throw new BusinessException("Please select the partnership role you are interested in");
    }
    if (request.getVisibilityScope() == null) {
      throw new BusinessException("Please select where the partnership should be visible");
    }
    if (request.getContributionMode() == null) {
      throw new BusinessException("Please select the proposed contribution type");
    }
  }

  /**
   * Campaign labels arrive from a public form, so they are trimmed and cut to the column width
   * rather than trusted to fit. A tag longer than the column would otherwise fail the insert and
   * lose the whole registration — the lead matters, the label does not.
   */
  private static String clip(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
  }

  /**
   * 64 random hex characters, from the same generator that backs {@code UUID.randomUUID}. The
   * unsubscribe link has to work without a login, so the token is the only thing standing between a
   * stranger and opting someone else out of their reminders — it is sized to not be guessable.
   */
  private static String newUnsubscribeToken() {
    return (UUID.randomUUID().toString() + UUID.randomUUID())
        .replace("-", "")
        .toLowerCase(Locale.ROOT);
  }

  private static ExhibitionInterestResponse toResponse(ExhibitionInterest e) {
    java.util.UUID sid = e.getSponsorship() != null ? e.getSponsorship().getId() : null;
    String sname = e.getSponsorship() != null ? e.getSponsorship().getName() : null;
    return ExhibitionInterestResponse.builder()
        .id(e.getId())
        .email(e.getEmail())
        .phoneNumber(e.getPhoneNumber())
        .interestType(e.getInterestType())
        .partnerRole(e.getPartnerRole() != null ? e.getPartnerRole().name() : null)
        .visibilityScope(e.getVisibilityScope() != null ? e.getVisibilityScope().name() : null)
        .contributionMode(e.getContributionMode() != null ? e.getContributionMode().name() : null)
        .company(e.getCompany())
        .message(e.getMessage())
        .organizationId(e.getOrganization() != null ? e.getOrganization().getId() : null)
        .sponsorshipId(sid)
        .sponsorshipPackageName(sname)
        .build();
  }
}
