package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.dto.AdminExhibitionInterestResponse;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.Sponsorship;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminExhibitionInterestService {

  private final ExhibitionInterestRepository repository;

  @Transactional(readOnly = true)
  public Page<AdminExhibitionInterestResponse> list(Pageable pageable) {
    return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
  }

  private AdminExhibitionInterestResponse toResponse(ExhibitionInterest e) {
    Organization o = e.getOrganization();
    Sponsorship s = e.getSponsorship();
    return AdminExhibitionInterestResponse.builder()
        .id(e.getId())
        .createdAt(e.getCreatedAt())
        .email(e.getEmail())
        .phoneNumber(e.getPhoneNumber())
        .interestType(e.getInterestType())
        .company(e.getCompany())
        .message(e.getMessage())
        .organizationId(o != null ? o.getId() : null)
        .organizationName(o != null ? o.getName() : null)
        .organizationType(o != null && o.getType() != null ? o.getType().name() : null)
        .organizationStatus(o != null && o.getStatus() != null ? o.getStatus().name() : null)
        .sponsorshipId(s != null ? s.getId() : null)
        .sponsorshipPackageName(s != null ? s.getName() : null)
        .build();
  }
}
