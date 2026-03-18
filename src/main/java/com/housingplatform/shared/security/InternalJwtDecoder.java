package com.housingplatform.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.Assert;

public class InternalJwtDecoder implements JwtDecoder {

  private final String jwtSecret;
  private final String jwtIssuer;
  private final TokenBlacklistService blacklistService;

  public InternalJwtDecoder() {
    this.jwtSecret =
        System.getProperty("jwt.secret", System.getenv().getOrDefault("JWT_SECRET", ""));
    this.jwtIssuer =
        System.getProperty(
            "jwt.issuer", System.getenv().getOrDefault("JWT_ISSUER", "housing-platform"));
    this.blacklistService = null;
    validateJwtSecret(this.jwtSecret);
  }

  public InternalJwtDecoder(String jwtSecret, String jwtIssuer) {
    this.jwtSecret = jwtSecret;
    this.jwtIssuer = jwtIssuer;
    this.blacklistService = null;
    validateJwtSecret(this.jwtSecret);
  }

  public InternalJwtDecoder(
      String jwtSecret, String jwtIssuer, TokenBlacklistService blacklistService) {
    this.jwtSecret = jwtSecret;
    this.jwtIssuer = jwtIssuer;
    this.blacklistService = blacklistService;
    validateJwtSecret(this.jwtSecret);
  }

  private void validateJwtSecret(String secret) {
    Assert.hasText(secret, "jwt.secret must be configured");
    Assert.isTrue(
        secret.length() >= 32, "jwt.secret must be at least 32 characters for HS256 signing");
  }

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public Jwt decode(String token) throws JwtException {
    try {
      // Check blacklist before doing any further validation.
      // A blacklisted token was explicitly invalidated on logout.
      if (blacklistService != null && blacklistService.isBlacklisted(token)) {
        throw new JwtException("JWT token has been invalidated (user logged out).");
      }

      Claims claims =
          Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();

      Instant issuedAt = claims.getIssuedAt().toInstant();
      Instant expiresAt = claims.getExpiration().toInstant();

      // Build JWT with all claims
      Jwt.Builder jwtBuilder =
          Jwt.withTokenValue(token)
              .header("alg", "HS256")
              .header("typ", "JWT")
              .issuedAt(issuedAt)
              .expiresAt(expiresAt)
              .issuer(claims.getIssuer())
              .subject(claims.getSubject());

      // Add custom claims
      if (claims.get("email") != null) {
        jwtBuilder.claim("email", claims.get("email"));
      }
      if (claims.get("scope") != null) {
        jwtBuilder.claim("scope", claims.get("scope"));
      }
      if (claims.get("roles") != null) {
        jwtBuilder.claim("roles", claims.get("roles"));
      }
      if (claims.get("organization_id") != null) {
        jwtBuilder.claim("organization_id", claims.get("organization_id"));
      }

      return jwtBuilder.build();
    } catch (JwtException e) {
      // Re-throw as-is so the caller gets the exact reason
      throw e;
    } catch (Exception e) {
      throw new JwtException("Failed to decode JWT token", e);
    }
  }
}
