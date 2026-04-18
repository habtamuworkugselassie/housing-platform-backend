package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.OrganizationDocumentReviewStatus;
import lombok.Data;

/** Partial update: only non-null fields are applied. Admin only. */
@Data
public class OrganizationDocumentReviewsPatchRequest {

  private OrganizationDocumentReviewStatus businessRegistrationReviewStatus;
  private String businessRegistrationReviewComment;

  private OrganizationDocumentReviewStatus licenseReviewStatus;
  private String licenseReviewComment;

  private OrganizationDocumentReviewStatus vatRegistrationReviewStatus;
  private String vatRegistrationReviewComment;

  private OrganizationDocumentReviewStatus tinRegistrationReviewStatus;
  private String tinRegistrationReviewComment;
}
