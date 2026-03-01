package com.housingplatform.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class JwtTokenProvider {

  @Value("${jwt.secret:}")
  private String jwtSecret;

  @Value("${jwt.expiration:3600}") // 1 hour default
  private Long jwtExpiration;

  @Value("${jwt.issuer:housing-platform}")
  private String jwtIssuer;

  private SecretKey getSigningKey() {
    Assert.hasText(jwtSecret, "jwt.secret must be configured");
    Assert.isTrue(
        jwtSecret.length() >= 32, "jwt.secret must be at least 32 characters for HS256 signing");
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(UUID userId, String email, List<String> scopes, List<String> roles) {
    return generateToken(userId, email, scopes, roles, null);
  }

  public String generateToken(
      UUID userId, String email, List<String> scopes, List<String> roles, UUID organizationId) {
    Instant now = Instant.now();
    Instant expiration = now.plus(jwtExpiration, ChronoUnit.SECONDS);

    // Build claims with all fields before calling build()
    var claimsBuilder =
        Jwts.claims()
            .subject(userId.toString())
            .issuer(jwtIssuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .add("email", email);

    // Add scopes before building
    if (scopes != null && !scopes.isEmpty()) {
      claimsBuilder.add("scope", String.join(" ", scopes));
    }

    // Add roles before building
    if (roles != null && !roles.isEmpty()) {
      claimsBuilder.add("roles", roles);
    }

    // Add organization_id if provided
    if (organizationId != null) {
      claimsBuilder.add("organization_id", organizationId.toString());
    }

    Claims claims = claimsBuilder.build();

    return Jwts.builder().claims(claims).signWith(getSigningKey()).compact();
  }

  public String generateRefreshToken(UUID userId) {
    Instant now = Instant.now();
    Instant expiration = now.plus(7, ChronoUnit.DAYS); // 7 days for refresh token

    return Jwts.builder()
        .subject(userId.toString())
        .issuer(jwtIssuer)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiration))
        .claim("type", "refresh")
        .signWith(getSigningKey())
        .compact();
  }

  public Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
  }

  public UUID getUserIdFromToken(String token) {
    Claims claims = parseClaims(token);
    return UUID.fromString(claims.getSubject());
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
