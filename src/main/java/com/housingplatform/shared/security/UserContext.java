package com.housingplatform.shared.security;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Utility class to extract user information from security context */
public class UserContext {

  public static UUID getCurrentUserId() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof Jwt jwt) {
      String userId = jwt.getClaimAsString("sub");
      if (userId != null) {
        return UUID.fromString(userId);
      }
    }
    throw new IllegalStateException("User ID not found in security context");
  }

  /** Current user id, or null when the request is unauthenticated/anonymous. */
  public static UUID getCurrentUserIdOrNull() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      return null;
    }
    String userId = jwt.getClaimAsString("sub");
    if (userId == null) {
      return null;
    }
    try {
      return UUID.fromString(userId);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static String getCurrentUserEmail() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof Jwt jwt) {
      return jwt.getClaimAsString("email");
    }
    return null;
  }

  /**
   * Exact match against the space-delimited {@code scope} claim. Must not be a substring test:
   * {@code "super_admin".contains("admin")} would otherwise grant admin rights to any scope that
   * merely embeds the word.
   */
  public static boolean hasScope(String scope) {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof Jwt jwt) {
      String scopes = jwt.getClaimAsString("scope");
      if (scopes == null || scopes.isBlank()) {
        return false;
      }
      for (String granted : scopes.trim().split("\\s+")) {
        if (granted.equals(scope)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean hasRole(String role) {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
  }

  /**
   * Check if the current user has admin scope
   *
   * @return true if user has admin scope, false otherwise
   */
  public static boolean isAdmin() {
    return hasScope(PortalScope.ADMIN);
  }

  /**
   * Check if the current user is a super admin (may manage admins and sponsor-company credentials).
   *
   * @return true if user has super admin scope, false otherwise
   */
  public static boolean isSuperAdmin() {
    return hasScope(PortalScope.SUPER_ADMIN);
  }

  public static java.util.Optional<UUID> getCurrentUserOrganizationId() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof Jwt jwt) {
      String orgId = jwt.getClaimAsString("organization_id");
      if (orgId != null) {
        try {
          return java.util.Optional.of(UUID.fromString(orgId));
        } catch (IllegalArgumentException e) {
          return java.util.Optional.empty();
        }
      }
    }
    return java.util.Optional.empty();
  }
}
