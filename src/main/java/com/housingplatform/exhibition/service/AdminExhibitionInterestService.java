package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.dto.AdminExhibitionInterestResponse;
import com.housingplatform.identity.dto.ProvisionOrganizationPrimaryUserRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminExhibitionInterestService {

  Page<AdminExhibitionInterestResponse> list(Pageable pageable);

  AdminExhibitionInterestResponse verifyContact(
      UUID interestId, ProvisionOrganizationPrimaryUserRequest request);
}
