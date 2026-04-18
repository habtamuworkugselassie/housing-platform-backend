package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.domain.SupplierSubcategory;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.OrganizationPhoneDto;
import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.dto.SupplierSubcategoryResponse;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.property.repository.ReviewRepository;
import com.housingplatform.shared.security.UserContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Organization} / {@link OrganizationContact} to API DTOs. Contact fields are stored in
 * {@link OrganizationContact} but exposed flat on {@link OrganizationResponse} for backward
 * compatibility.
 */
@Component
public class OrganizationMapper {

  @Autowired private ReviewRepository reviewRepository;
  @Autowired private RealEstateAgentRepository realEstateAgentRepository;
  @Autowired private UserRepository userRepository;

  /**
   * Expose admin document review fields to admins and to members of this organization:
   *
   * <ul>
   *   <li>Primary contact (any org type: bank, supplier, real estate, etc.)
   *   <li>JWT {@code organization_id} claim matches (set from agent link or {@link
   *       User#getOrganization()} at login — covers bankers, suppliers, realtors)
   *   <li>{@link User#getOrganization()} matches (banker/supplier/others linked via user row when
   *       JWT claim is absent or stale)
   *   <li>Real-estate agent row points at this organization
   * </ul>
   *
   * Not exposed to anonymous users or to authenticated users viewing another org’s public profile.
   */
  private boolean shouldExposeDocumentReviews(Organization organization) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return false;
    }
    if (UserContext.isAdmin()) {
      return true;
    }
    final UUID userId;
    try {
      userId = UserContext.getCurrentUserId();
    } catch (IllegalStateException e) {
      return false;
    }
    final UUID orgId = organization.getId();
    if (organization.getPrimaryContact() != null
        && organization.getPrimaryContact().getId().equals(userId)) {
      return true;
    }
    Optional<UUID> jwtOrg = UserContext.getCurrentUserOrganizationId();
    if (jwtOrg.isPresent() && jwtOrg.get().equals(orgId)) {
      return true;
    }
    Optional<User> userOpt = userRepository.findById(userId);
    if (userOpt.isPresent()) {
      User u = userOpt.get();
      if (u.getOrganization() != null && u.getOrganization().getId().equals(orgId)) {
        return true;
      }
    }
    return realEstateAgentRepository
        .findByUserId(userId)
        .map(a -> a.getOrganizationId().equals(orgId))
        .orElse(false);
  }

  public Organization toEntity(OrganizationRequest request) {
    Organization organization =
        Organization.builder()
            .name(request.getName())
            .registrationNumber(request.getRegistrationNumber())
            .businessRegistration(request.getBusinessRegistration())
            .license(request.getLicense())
            .vatRegistration(request.getVatRegistration())
            .tinRegistration(request.getTinRegistration())
            .businessRegistrationNumber(request.getBusinessRegistrationNumber())
            .licenseNumber(request.getLicenseNumber())
            .vatNumber(request.getVatNumber())
            .tinNumber(request.getTinNumber())
            .type(request.getType())
            .address(request.getAddress())
            .city(request.getCity())
            .country(request.getCountry())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .description(request.getDescription())
            .build();

    OrganizationContact contact =
        OrganizationContact.builder()
            .organization(organization)
            .email(request.getEmail())
            .website(request.getWebsite())
            .facebookUrl(request.getFacebookUrl())
            .instagramUrl(request.getInstagramUrl())
            .linkedinUrl(request.getLinkedinUrl())
            .twitterUrl(request.getTwitterUrl())
            .youtubeUrl(request.getYoutubeUrl())
            .build();
    organization.setContact(contact);
    return organization;
  }

  /** Updates only non-null fields from the request (same behavior as MapStruct IGNORE). */
  public void updateEntity(Organization organization, OrganizationRequest request) {
    if (request.getName() != null) {
      organization.setName(request.getName());
    }
    if (request.getRegistrationNumber() != null) {
      organization.setRegistrationNumber(request.getRegistrationNumber());
    }
    if (request.getBusinessRegistration() != null) {
      organization.setBusinessRegistration(request.getBusinessRegistration());
    }
    if (request.getLicense() != null) {
      organization.setLicense(request.getLicense());
    }
    if (request.getVatRegistration() != null) {
      organization.setVatRegistration(request.getVatRegistration());
    }
    if (request.getTinRegistration() != null) {
      organization.setTinRegistration(request.getTinRegistration());
    }
    if (request.getBusinessRegistrationNumber() != null) {
      organization.setBusinessRegistrationNumber(request.getBusinessRegistrationNumber());
    }
    if (request.getLicenseNumber() != null) {
      organization.setLicenseNumber(request.getLicenseNumber());
    }
    if (request.getVatNumber() != null) {
      organization.setVatNumber(request.getVatNumber());
    }
    if (request.getTinNumber() != null) {
      organization.setTinNumber(request.getTinNumber());
    }
    if (request.getType() != null) {
      organization.setType(request.getType());
    }
    if (request.getAddress() != null) {
      organization.setAddress(request.getAddress());
    }
    if (request.getCity() != null) {
      organization.setCity(request.getCity());
    }
    if (request.getCountry() != null) {
      organization.setCountry(request.getCountry());
    }
    if (request.getLatitude() != null) {
      organization.setLatitude(request.getLatitude());
    }
    if (request.getLongitude() != null) {
      organization.setLongitude(request.getLongitude());
    }
    if (request.getDescription() != null) {
      organization.setDescription(request.getDescription());
    }

    OrganizationContact c = organization.getContact();
    if (c == null) {
      c = OrganizationContact.builder().organization(organization).build();
      organization.setContact(c);
    }
    if (request.getEmail() != null) {
      c.setEmail(request.getEmail());
    }
    if (request.getWebsite() != null) {
      c.setWebsite(request.getWebsite());
    }
    if (request.getFacebookUrl() != null) {
      c.setFacebookUrl(request.getFacebookUrl());
    }
    if (request.getInstagramUrl() != null) {
      c.setInstagramUrl(request.getInstagramUrl());
    }
    if (request.getLinkedinUrl() != null) {
      c.setLinkedinUrl(request.getLinkedinUrl());
    }
    if (request.getTwitterUrl() != null) {
      c.setTwitterUrl(request.getTwitterUrl());
    }
    if (request.getYoutubeUrl() != null) {
      c.setYoutubeUrl(request.getYoutubeUrl());
    }
  }

  private List<SupplierSubcategoryResponse> mapSupplierSubcategories(Organization organization) {
    if (organization.getSupplierSubcategories() == null
        || organization.getSupplierSubcategories().isEmpty()) {
      return List.of();
    }
    return organization.getSupplierSubcategories().stream()
        .sorted(
            Comparator.comparingInt(SupplierSubcategory::getSortOrder)
                .thenComparing(
                    s -> s.getName() != null ? s.getName() : "", String.CASE_INSENSITIVE_ORDER))
        .map(
            s ->
                SupplierSubcategoryResponse.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .slug(s.getSlug())
                    .sortOrder(s.getSortOrder())
                    .active(s.isActive())
                    .build())
        .toList();
  }

  public OrganizationResponse toResponse(Organization organization) {
    if (organization == null) {
      return null;
    }
    OrganizationContact c = organization.getContact();
    List<OrganizationPhoneDto> phoneDtos = new ArrayList<>();
    if (c != null && c.getPhones() != null) {
      for (OrganizationPhone p : c.getPhones()) {
        phoneDtos.add(
            OrganizationPhoneDto.builder()
                .countryCode(p.getCountryCode() != null ? p.getCountryCode() : "+251")
                .number(p.getNumber() != null ? p.getNumber() : "")
                .build());
      }
    }
    var responseBuilder =
        OrganizationResponse.builder()
            .id(organization.getId())
            .name(organization.getName())
            .registrationNumber(organization.getRegistrationNumber())
            .businessRegistration(organization.getBusinessRegistration())
            .license(organization.getLicense())
            .vatRegistration(organization.getVatRegistration())
            .tinRegistration(organization.getTinRegistration())
            .businessRegistrationNumber(organization.getBusinessRegistrationNumber())
            .licenseNumber(organization.getLicenseNumber())
            .vatNumber(organization.getVatNumber())
            .tinNumber(organization.getTinNumber())
            .type(organization.getType())
            .status(organization.getStatus())
            .address(organization.getAddress())
            .city(organization.getCity())
            .country(organization.getCountry())
            .latitude(organization.getLatitude())
            .longitude(organization.getLongitude())
            .phoneNumbers(phoneDtos)
            .email(c != null ? c.getEmail() : null)
            .website(c != null ? c.getWebsite() : null)
            .facebookUrl(c != null ? c.getFacebookUrl() : null)
            .instagramUrl(c != null ? c.getInstagramUrl() : null)
            .linkedinUrl(c != null ? c.getLinkedinUrl() : null)
            .twitterUrl(c != null ? c.getTwitterUrl() : null)
            .youtubeUrl(c != null ? c.getYoutubeUrl() : null)
            .description(organization.getDescription())
            .primaryContactUserId(
                organization.getPrimaryContact() != null
                    ? organization.getPrimaryContact().getId()
                    : null)
            .createdAt(organization.getCreatedAt())
            .updatedAt(organization.getUpdatedAt())
            .verified(organization.isVerified())
            .verificationLevel(
                organization.getVerificationLevel() != null
                    ? organization.getVerificationLevel().name()
                    : null)
            .supplierSubcategories(mapSupplierSubcategories(organization))
            .averageRating(
                reviewRepository != null
                    ? reviewRepository.getAverageRatingForOrganization(organization.getId())
                    : null)
            .reviewCount(
                reviewRepository != null
                    ? reviewRepository.getReviewCountForOrganization(organization.getId())
                    : 0);

    if (shouldExposeDocumentReviews(organization)) {
      responseBuilder
          .businessRegistrationReviewStatus(organization.getBusinessRegistrationReviewStatus())
          .businessRegistrationReviewComment(organization.getBusinessRegistrationReviewComment())
          .licenseReviewStatus(organization.getLicenseReviewStatus())
          .licenseReviewComment(organization.getLicenseReviewComment())
          .vatRegistrationReviewStatus(organization.getVatRegistrationReviewStatus())
          .vatRegistrationReviewComment(organization.getVatRegistrationReviewComment())
          .tinRegistrationReviewStatus(organization.getTinRegistrationReviewStatus())
          .tinRegistrationReviewComment(organization.getTinRegistrationReviewComment());
    }

    return responseBuilder.build();
  }
}
