package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.dto.OrganizationPhoneDto;
import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrganizationMapper {
  @Mapping(target = "phones", ignore = true)
  Organization toEntity(OrganizationRequest request);

  default OrganizationResponse toResponse(Organization organization) {
    if (organization == null) {
      return null;
    }
    List<OrganizationPhoneDto> phoneDtos = new ArrayList<>();
    if (organization.getPhones() != null) {
      for (OrganizationPhone p : organization.getPhones()) {
        phoneDtos.add(
            OrganizationPhoneDto.builder()
                .countryCode(p.getCountryCode() != null ? p.getCountryCode() : "+251")
                .number(p.getNumber() != null ? p.getNumber() : "")
                .build());
      }
    }
    OrganizationResponse response =
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
            .email(organization.getEmail())
            .website(organization.getWebsite())
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
    return response;
  }

  @Mapping(target = "phones", ignore = true)
  void updateEntity(@MappingTarget Organization organization, OrganizationRequest request);
}
