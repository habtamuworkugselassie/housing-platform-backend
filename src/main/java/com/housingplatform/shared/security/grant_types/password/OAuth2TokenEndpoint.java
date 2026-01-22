package com.housingplatform.shared.security.grant_types.password;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.shared.security.JwtTokenProvider;
import com.housingplatform.shared.security.PortalScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OAuth2 Token Endpoint for Password Grant Type
 * Handles /oauth2/token requests with grant_type=password
 */
@Slf4j
@RestController
@RequestMapping("/oauth2/token")
@RequiredArgsConstructor
public class OAuth2TokenEndpoint {
    
    private final OAuth2PasswordAuthenticationProvider passwordAuthenticationProvider;
    private final RealEstateAgentRepository realEstateAgentRepository;
    private final JwtTokenProvider jwtTokenProvider;
    
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(@RequestParam Map<String, String> parameters) {
        try {
            // Create authentication token from request
            OAuth2PasswordAuthenticationToken authRequest = new OAuth2PasswordAuthenticationToken(
                    parameters.get("username"),
                    parameters.get("password"),
                    SecurityContextHolder.getContext().getAuthentication(),
                    parameters.containsKey("scope") ? 
                            Set.of(parameters.get("scope").split(" ")) : null,
                    new HashMap<>(parameters)
            );
            
            // Authenticate
            Authentication authentication = passwordAuthenticationProvider.authenticate(authRequest);
            
            if (!authentication.isAuthenticated()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "Authentication failed", null));
            }
            
            // Get user details
            User user = (User) authentication.getDetails();
            
            // Extract scopes
            Set<String> scopes = authRequest.getScopes();
            if (scopes == null || scopes.isEmpty()) {
                // Use default scopes from user roles
                scopes = user.getRoles().stream()
                        .map(this::mapRoleToScope)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
            }
            
            // Generate tokens
            List<String> scopeList = new ArrayList<>(scopes);
            List<String> roles = user.getRoles().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
            
            // Get organization_id if user is a real estate agent
            UUID organizationId = null;
            Optional<RealEstateAgent> agent = realEstateAgentRepository.findByUserId(user.getId());
            if (agent.isPresent()) {
                organizationId = agent.get().getOrganizationId();
            }
            
            String accessTokenValue = jwtTokenProvider.generateToken(
                    user.getId(),
                    user.getEmail(),
                    scopeList,
                    roles,
                    organizationId
            );
            
            String refreshTokenValue = jwtTokenProvider.generateRefreshToken(user.getId());
            
            // Parse access token to get expiration
            var claims = jwtTokenProvider.parseClaims(accessTokenValue);
            Instant issuedAt = claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration().toInstant();
            long expiresIn = expiresAt.getEpochSecond() - issuedAt.getEpochSecond();
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", accessTokenValue);
            response.put("token_type", "Bearer");
            response.put("expires_in", expiresIn);
            response.put("refresh_token", refreshTokenValue);
            response.put("scope", String.join(" ", scopes));
            
            return ResponseEntity.ok(response);
            
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (BadCredentialsException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "Invalid credentials", null));
        } catch (Exception e) {
            log.error("Token endpoint error", e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "Token generation failed", null));
        }
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
