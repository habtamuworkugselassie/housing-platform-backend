package com.housingplatform.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * The decoder is the single gate every request token passes through. These cover the two holes
 * closed here: a refresh token used as an access token, and a token minted under the wrong issuer.
 */
class InternalJwtDecoderTest {

  private static final String SECRET =
      "test-secret-key-for-testing-purposes-only-minimum-32-characters";
  private static final String ISSUER = "housing-platform-test";

  private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
  private final InternalJwtDecoder decoder = new InternalJwtDecoder(SECRET, ISSUER);

  private String accessToken(String issuer) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(UUID.randomUUID().toString())
        .issuer(issuer)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
        .claim("email", "user@example.com")
        .claim("scope", "buyer")
        .signWith(key)
        .compact();
  }

  private String refreshToken() {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(UUID.randomUUID().toString())
        .issuer(ISSUER)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(7, ChronoUnit.DAYS)))
        .claim("type", "refresh")
        .signWith(key)
        .compact();
  }

  @Test
  void decodesAValidAccessToken() {
    Jwt jwt = decoder.decode(accessToken(ISSUER));
    assertThat(jwt.getClaimAsString("scope")).isEqualTo("buyer");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
  }

  @Test
  void rejectsARefreshTokenUsedAsAnAccessToken() {
    assertThatThrownBy(() -> decoder.decode(refreshToken()))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("Refresh tokens cannot be used");
  }

  @Test
  void rejectsATokenMintedUnderTheWrongIssuer() {
    assertThatThrownBy(() -> decoder.decode(accessToken("some-other-issuer")))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("issuer");
  }
}
