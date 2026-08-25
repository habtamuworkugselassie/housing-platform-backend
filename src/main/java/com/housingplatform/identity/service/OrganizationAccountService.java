package com.housingplatform.identity.service;

import com.housingplatform.identity.dto.CreateOrganizationAccountRequest;
import com.housingplatform.identity.dto.OrganizationAccountResponse;
import com.housingplatform.identity.dto.SetAccountPasswordRequest;
import com.housingplatform.identity.dto.UpdateAccountStatusRequest;
import java.util.List;
import java.util.UUID;

/**
 * Super-admin control over the login credentials of sponsor companies — banks, real-estate firms,
 * suppliers, contractors, media partners and the rest.
 *
 * <p>Distinct from {@link OrganizationPrimaryUserProvisioningService}, which mints the single
 * primary contact as a side effect of the exhibition and sponsorship review workflows. This service
 * is the direct, deliberate path: an operator opens a company and issues, resets or revokes its
 * accounts, including additional staff logins beyond the primary contact.
 */
public interface OrganizationAccountService {

  /** All logins linked to the organization, primary contact first. */
  List<OrganizationAccountResponse> getAccounts(UUID organizationId);

  /** Creates a login for the organization with an admin-chosen password. */
  OrganizationAccountResponse createAccount(
      UUID organizationId, CreateOrganizationAccountRequest request);

  /** Replaces the account's password. The user is not emailed; the operator hands it over. */
  OrganizationAccountResponse setPassword(
      UUID organizationId, UUID userId, SetAccountPasswordRequest request);

  /** Enables, disables or suspends the account. */
  OrganizationAccountResponse setStatus(
      UUID organizationId, UUID userId, UpdateAccountStatusRequest request);

  /** Promotes the account to the organization's primary contact (and real-estate super agent). */
  OrganizationAccountResponse makePrimaryContact(UUID organizationId, UUID userId);

  /**
   * Detaches the account from the organization without deleting the person. Refused for the primary
   * contact — promote a replacement first.
   */
  void unlinkAccount(UUID organizationId, UUID userId);
}
