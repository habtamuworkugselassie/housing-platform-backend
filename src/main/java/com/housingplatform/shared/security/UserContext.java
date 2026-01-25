package com.housingplatform.shared.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Utility class to extract user information from security context
 */
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
    
    public static String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getClaimAsString("email");
        }
        return null;
    }
    
    public static boolean hasScope(String scope) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt) {
            String scopes = jwt.getClaimAsString("scope");
            return scopes != null && scopes.contains(scope);
        }
        return false;
    }
    
    public static boolean hasRole(String role) {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
    
    /**
     * Check if the current user has admin scope
     * @return true if user has admin scope, false otherwise
     */
    public static boolean isAdmin() {
        return hasScope(PortalScope.ADMIN);
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
