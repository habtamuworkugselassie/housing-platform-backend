package com.housingplatform.shared.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom JWT Authentication Token that extends JwtAuthenticationToken
 * to provide easy access to scopes and user information
 */
public class HousingPlatformJwtAuthenticationToken extends JwtAuthenticationToken {
    
    public static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";
    
    private final Set<String> scopes;
    private final Set<String> roles;
    
    public HousingPlatformJwtAuthenticationToken(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(jwt, authorities);
        this.scopes = extractScopes(jwt);
        this.roles = extractRoles(jwt);
    }
    
    private Set<String> extractScopes(Jwt jwt) {
        String scopeClaim = jwt.getClaimAsString("scope");
        if (scopeClaim != null && !scopeClaim.isEmpty()) {
            return Set.of(scopeClaim.split(" "));
        }
        // Also check authorities for SCOPE_ prefix
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith(SCOPE_AUTHORITY_PREFIX))
                .map(auth -> auth.substring(SCOPE_AUTHORITY_PREFIX.length()))
                .collect(Collectors.toSet());
    }
    
    private Set<String> extractRoles(Jwt jwt) {
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof Collection) {
            return ((Collection<?>) rolesClaim).stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }
    
    public Set<String> getScopes() {
        return scopes;
    }
    
    public Set<String> getRoles() {
        return roles;
    }
    
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
    
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
    
    public String getUserId() {
        return getToken().getSubject();
    }
    
    public String getEmail() {
        return getToken().getClaimAsString("email");
    }
}
