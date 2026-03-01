package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.dto.ExhibitionInterestRequest;
import com.housingplatform.exhibition.dto.ExhibitionInterestResponse;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExhibitionInterestService {

  private final ExhibitionInterestRepository repository;
  private final OrganizationRepository organizationRepository;

  @Transactional
  public ExhibitionInterestResponse register(ExhibitionInterestRequest request) {
    String email = request.getEmail().trim().toLowerCase();
    String company = request.getCompany() != null ? request.getCompany().trim() : null;
    String phoneNumber = request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null;
    String orgName =
        (company != null && !company.isEmpty())
            ? company
            : ("Exhibition: " + email);

    Organization organization =
        Organization.builder()
            .name(orgName)
            .type(Organization.OrganizationType.valueOf(request.getOrganizationType()))
            .status(Organization.OrganizationStatus.PENDING_APPROVAL)
            .email(email)
            .phoneNumber(phoneNumber)
            .description(
                request.getMessage() != null && !request.getMessage().trim().isEmpty()
                    ? request.getMessage().trim()
                    : null)
            .build();
    organization = organizationRepository.save(organization);

    ExhibitionInterest entity =
        ExhibitionInterest.builder()
            .email(email)
            .phoneNumber(phoneNumber)
            .interestType(request.getInterestType().trim().toLowerCase())
            .company(company)
            .message(request.getMessage() != null ? request.getMessage().trim() : null)
            .organization(organization)
            .build();
    entity = repository.save(entity);

    return toResponse(entity);
  }

  private static ExhibitionInterestResponse toResponse(ExhibitionInterest e) {
    return ExhibitionInterestResponse.builder()
        .id(e.getId())
        .email(e.getEmail())
        .phoneNumber(e.getPhoneNumber())
        .interestType(e.getInterestType())
        .company(e.getCompany())
        .message(e.getMessage())
        .organizationId(e.getOrganization() != null ? e.getOrganization().getId() : null)
        .build();
  }
}
