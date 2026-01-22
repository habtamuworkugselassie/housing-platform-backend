package com.housingplatform.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

public class InternalJwtDecoder implements JwtDecoder {
    
    private final String jwtSecret;
    private final String jwtIssuer;
    
    public InternalJwtDecoder() {
        // Default values - will be overridden by Spring
        this.jwtSecret = System.getProperty("jwt.secret", 
            System.getenv().getOrDefault("JWT_SECRET", 
                "your-256-bit-secret-key-change-this-in-production-minimum-32-characters"));
        this.jwtIssuer = System.getProperty("jwt.issuer",
            System.getenv().getOrDefault("JWT_ISSUER", "housing-platform"));
    }
    
    public InternalJwtDecoder(String jwtSecret, String jwtIssuer) {
        this.jwtSecret = jwtSecret;
        this.jwtIssuer = jwtIssuer;
    }
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
    
    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            Instant issuedAt = claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration().toInstant();
            
            // Build JWT with all claims
            Jwt.Builder jwtBuilder = Jwt.withTokenValue(token)
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
        } catch (Exception e) {
            throw new JwtException("Failed to decode JWT token", e);
        }
    }
}
