package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;

/** Rules for which organizations appear on public/unauthenticated marketplace APIs. */
public final class OrganizationPublicVisibility {

  private OrganizationPublicVisibility() {}

  /**
   * True when the organization may appear on public listings (marketplace, property search, etc.).
   * Only {@link Organization.OrganizationStatus#APPROVED} organizations are listed publicly.
   */
  public static boolean isPubliclyListed(Organization organization) {
    if (organization == null) {
      return false;
    }
    return organization.getStatus() == Organization.OrganizationStatus.APPROVED;
  }
}
