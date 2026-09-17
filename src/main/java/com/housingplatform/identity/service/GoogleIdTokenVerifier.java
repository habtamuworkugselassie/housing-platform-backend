package com.housingplatform.identity.service;

import com.housingplatform.identity.config.GoogleAuthProperties;
import com.housingplatform.shared.exception.BusinessException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies a Google ID token the browser obtained from Google Identity Services: signature against
 * Google's published keys, issuer, audience (our client id) and expiry. Only then is the identity
 * inside trusted.
 */
@Component
@RequiredArgsConstructor
public class GoogleIdTokenVerifier {

  private static final Set<String> ISSUERS =
      Set.of("accounts.google.com", "https://accounts.google.com");

  private final GoogleAuthProperties properties;
  private volatile JwtDecoder decoder;

  /** What Google asserts about the person. */
  public record GoogleIdentity(
      String subject,
      String email,
      boolean emailVerified,
      String givenName,
      String familyName,
      String fullName,
      String pictureUrl) {}

  public boolean isConfigured() {
    return properties.isConfigured();
  }

  public GoogleIdentity verify(String idToken) {
    if (!properties.isConfigured()) {
      throw new BusinessException("Google sign-in is not configured on this server");
    }
    if (idToken == null || idToken.isBlank()) {
      throw new BusinessException("Google credential is required");
    }
    Jwt jwt;
    try {
      jwt = decoder().decode(idToken);
    } catch (JwtException e) {
      throw new BusinessException("Google sign-in could not be verified. Please try again.");
    }
    return new GoogleIdentity(
        jwt.getSubject(),
        jwt.getClaimAsString("email"),
        Boolean.TRUE.equals(jwt.getClaim("email_verified")),
        jwt.getClaimAsString("given_name"),
        jwt.getClaimAsString("family_name"),
        jwt.getClaimAsString("name"),
        jwt.getClaimAsString("picture"));
  }

  private JwtDecoder decoder() {
    JwtDecoder local = decoder;
    if (local == null) {
      synchronized (this) {
        if (decoder == null) {
          NimbusJwtDecoder nimbus =
              NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
          OAuth2TokenValidator<Jwt> issuer =
              new JwtClaimValidator<String>(JwtClaimNames.ISS, ISSUERS::contains);
          OAuth2TokenValidator<Jwt> audience =
              new JwtClaimValidator<List<String>>(
                  JwtClaimNames.AUD, aud -> aud != null && aud.contains(properties.getClientId()));
          nimbus.setJwtValidator(
              new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience));
          decoder = nimbus;
        }
        local = decoder;
      }
    }
    return local;
  }
}
