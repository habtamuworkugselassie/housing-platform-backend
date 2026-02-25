package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrganizationMapper {
  Organization toEntity(OrganizationRequest request);

  default OrganizationResponse toResponse(Organization organization) {
    if (organization == null) {
      return null;
    }
    OrganizationResponse response =
        OrganizationResponse.builder()
            .id(organization.getId())
            .name(organization.getName())
            .registrationNumber(organization.getRegistrationNumber())
            .type(organization.getType())
            .status(organization.getStatus())
            .address(organization.getAddress())
            .city(organization.getCity())
            .country(organization.getCountry())
            .phoneNumber(organization.getPhoneNumber())
            .email(organization.getEmail())
            .website(organization.getWebsite())
            .description(organization.getDescription())
            .primaryContactUserId(
                organization.getPrimaryContact() != null
                    ? organization.getPrimaryContact().getId()
                    : null)
            .createdAt(organization.getCreatedAt())
            .updatedAt(organization.getUpdatedAt())
            .build();
    return response;
  }

  void updateEntity(@MappingTarget Organization organization, OrganizationRequest request);
}
