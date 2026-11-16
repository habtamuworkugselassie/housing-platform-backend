package com.housingplatform.construction;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Puts a JWT principal with the given user id, organization and scope into the context. */
public final class ConstructionTestAuth {

  private ConstructionTestAuth() {}

  public static void signIn(UUID userId, UUID organizationId, String scope) {
    Jwt.Builder builder =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("scope", scope)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
    if (organizationId != null) {
      builder.claim("organization_id", organizationId.toString());
    }
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(builder.build()));
  }

  public static void clear() {
    SecurityContextHolder.clearContext();
  }
}
