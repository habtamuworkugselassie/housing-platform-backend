package com.housingplatform.shared.security.grant_types.password;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.security.JwtTokenProvider;
import com.housingplatform.shared.security.PortalScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OAuth2 Password Grant Authentication Provider
 * Handles password-based authentication and generates JWT tokens
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2PasswordAuthenticationProvider implements AuthenticationProvider {
    
    private final UserRepository userRepository;
    private final RealEstateAgentRepository realEstateAgentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2PasswordAuthenticationToken passwordToken = (OAuth2PasswordAuthenticationToken) authentication;
        
        try {
            // Find user by email, username, or phone number
            User user = userRepository.findByEmail(passwordToken.getUsername())
                    .orElseGet(() -> userRepository.findByPhoneNumber(passwordToken.getUsername())
                            .orElseThrow(() -> new BadCredentialsException("Invalid credentials")));
            
            // Verify password
            if (!passwordEncoder.matches(passwordToken.getPassword(), user.getPasswordHash())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            
            // Check if user is active
            if (user.getStatus() != User.UserStatus.ACTIVE) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, "User account is not active", null));
            }
            
            // Extract scopes from user roles
            List<String> scopes = user.getRoles().stream()
                    .map(this::mapRoleToScope)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            // Filter requested scopes (if any)
            Set<String> requestedScopes = passwordToken.getScopes();
            if (requestedScopes != null && !requestedScopes.isEmpty()) {
                // Validate requested scopes are available to user
                Set<String> availableScopes = new HashSet<>(scopes);
                availableScopes.add("admin"); // Admin always has access
                
                Set<String> authorizedScopes = requestedScopes.stream()
                        .filter(availableScopes::contains)
                        .collect(Collectors.toSet());
                
                if (authorizedScopes.isEmpty() && !requestedScopes.isEmpty()) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE, "Invalid scope requested", null));
                }
                
                scopes = new ArrayList<>(authorizedScopes);
            }
            
            // Extract role names
            List<String> roles = user.getRoles().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
            
            // Get organization_id if user is a real estate agent
            UUID organizationId = null;
            Optional<RealEstateAgent> agent = realEstateAgentRepository.findByUserId(user.getId());
            if (agent.isPresent()) {
                organizationId = agent.get().getOrganizationId();
            }
            
            // Generate access token
            String accessTokenValue = jwtTokenProvider.generateToken(
                    user.getId(),
                    user.getEmail(),
                    scopes,
                    roles,
                    organizationId
            );
            
            // Parse the JWT to get expiration
            var claims = jwtTokenProvider.parseClaims(accessTokenValue);
            Instant issuedAt = claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration().toInstant();
            
            // Create OAuth2AccessToken
            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    accessTokenValue,
                    issuedAt,
                    expiresAt,
                    new HashSet<>(scopes)
            );
            
            // Create JWT for authentication token
            var jwtBuilder = org.springframework.security.oauth2.jwt.Jwt.withTokenValue(accessTokenValue)
                    .header("alg", "HS256")
                    .header("typ", "JWT")
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .subject(user.getId().toString())
                    .claim("email", user.getEmail())
                    .claim("scope", String.join(" ", scopes))
                    .claim("roles", roles);
            
            // Add organization_id if present
            if (organizationId != null) {
                jwtBuilder.claim("organization_id", organizationId.toString());
            }
            
            Jwt jwt = jwtBuilder.build();
            
            // Create authorities from scopes
            var authorities = scopes.stream()
                    .map(scope -> (org.springframework.security.core.GrantedAuthority) 
                            () -> "SCOPE_" + scope)
                    .collect(Collectors.toList());
            
            JwtAuthenticationToken jwtAuthToken = new JwtAuthenticationToken(jwt, authorities);
            
            // Create successful authentication
            OAuth2PasswordAuthenticationToken authenticatedToken = new OAuth2PasswordAuthenticationToken(
                    user.getEmail(),
                    null, // Clear password
                    passwordToken.getClientPrincipal(),
                    new HashSet<>(scopes),
                    passwordToken.getAdditionalParameters()
            );
            authenticatedToken.setAuthenticated(true);
            authenticatedToken.setDetails(user);
            
            return authenticatedToken;
            
        } catch (BadCredentialsException e) {
            log.info("Authentication failed: {}", e.getMessage());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "Invalid credentials", null));
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Authentication error", e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "Authentication failed", null));
        }
    }
    
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2PasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
    
    private String mapRoleToScope(User.UserRole role) {
        return switch (role) {
            case BUYER -> PortalScope.BUYER;
            case BANKER -> PortalScope.BANKER;
            case REALTOR -> PortalScope.REALTOR;
            case SUPPLIER -> PortalScope.SUPPLIER;
            case ADMIN -> PortalScope.ADMIN;
        };
    }
}
