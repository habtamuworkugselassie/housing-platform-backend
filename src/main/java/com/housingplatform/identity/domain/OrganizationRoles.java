package com.housingplatform.identity.domain;

import java.util.HashSet;
import java.util.Set;

/**
 * Canonical mapping from {@link Organization.OrganizationType} to the portal role its staff log in
 * with. Sponsor companies outside the three first-class portals (contractors, developers, insurers,
 * consultants, media houses) share the supplier portal, which is what the marketplace already
 * assumes for them.
 *
 * <p>Single source of truth for both credential provisioning and the organization-link validation
 * on {@link User}; keeping them in step is what lets an admin issue a login for <em>any</em>
 * sponsor company rather than only banks, real-estate firms and suppliers.
 */
public final class OrganizationRoles {

  private OrganizationRoles() {}

  /** Portal role for staff of the given organization type. Never null. */
  public static User.UserRole portalRoleFor(Organization.OrganizationType type) {
    if (type == null) {
      return User.UserRole.SUPPLIER;
    }
    return switch (type) {
      case REAL_ESTATE_COMPANY -> User.UserRole.REALTOR;
      case BANK -> User.UserRole.BANKER;
      case SUPPLIER,
          CONTRACTOR,
          DEVELOPER,
          INSURANCE,
          CONSULTANT_ARCHITECT,
          FINISHING_CONTRACTOR,
          MEDIA_COMPANY -> User.UserRole.SUPPLIER;
    };
  }

  /** Mutable default role set for a newly provisioned organization account. */
  public static Set<User.UserRole> defaultRolesFor(Organization.OrganizationType type) {
    Set<User.UserRole> roles = new HashSet<>();
    roles.add(portalRoleFor(type));
    return roles;
  }

  /**
   * True when at least one of {@code roles} is the portal role this organization type logs in with.
   * Privileged roles are ignored here: an admin may also hold a company account.
   */
  public static boolean matchesPortalRole(
      Organization.OrganizationType type, Set<User.UserRole> roles) {
    return roles != null && roles.contains(portalRoleFor(type));
  }
}
