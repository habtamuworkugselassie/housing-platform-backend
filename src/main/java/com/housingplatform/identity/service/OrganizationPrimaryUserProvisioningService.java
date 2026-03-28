package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.ProvisionOrganizationPrimaryUserRequest;

/**
 * Creates the organization's {@link Organization#getPrimaryContact()} user when missing, using the
 * email on {@link com.housingplatform.identity.domain.OrganizationContact} (the same email the lead
 * submitted).
 */
public interface OrganizationPrimaryUserProvisioningService {

  /**
   * @param emailFallback used only when org contact email is absent (e.g. legacy row); normally org
   *     contact carries the submitted email
   * @param phoneFallback optional phone when org contact has no phone lines
   */
  User provisionPrimaryContactIfMissing(
      Organization org,
      ProvisionOrganizationPrimaryUserRequest request,
      String emailFallback,
      String phoneFallback);

  /**
   * Links the exhibition registrant to the new organization: reuses an existing user with no
   * organization, or creates a {@link User.UserStatus#PENDING_VERIFICATION} primary contact (admin
   * completes login via verify-contact). Idempotent when {@link Organization#getPrimaryContact()}
   * is already set.
   */
  User linkExhibitionLeadUser(Organization org, String emailFallback, String phoneFallback);

  /** Applies admin-chosen password and name to a lead user created at exhibition registration. */
  void completePendingPrimaryContact(User user, ProvisionOrganizationPrimaryUserRequest request);
}
