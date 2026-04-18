package com.housingplatform.identity.domain;

/** Admin workflow for a single organization verification document. */
public enum OrganizationDocumentReviewStatus {
  PENDING,
  APPROVED,
  REJECTED,
  NEEDS_REVISION
}
