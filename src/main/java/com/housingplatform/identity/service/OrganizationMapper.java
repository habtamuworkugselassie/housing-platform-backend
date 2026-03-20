package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.dto.OrganizationPhoneDto;
import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Organization} / {@link OrganizationContact} to API DTOs. Contact fields are stored in
 * {@link OrganizationContact} but exposed flat on {@link OrganizationResponse} for backward
 * compatibility.
 */
@Component
public class OrganizationMapper {

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
    return OrganizationResponse.builder()
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
        .build();
  }
}
