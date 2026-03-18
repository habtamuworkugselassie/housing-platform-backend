package com.housingplatform.shared.security;

import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Manages a Redis-backed blacklist of invalidated JWT access tokens.
 *
 * <p>When a user logs out, their current access token is stored here with a TTL equal to its
 * remaining validity period. Any subsequent request bearing a blacklisted token is rejected with
 * 401, even if the token's signature and expiry would otherwise be valid.
 *
 * <p>Redis auto-expires entries, so the blacklist never grows unbounded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

  private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

  private final StringRedisTemplate redisTemplate;

  /**
   * Adds a token to the blacklist. The TTL is set to the token's remaining lifetime so that Redis
   * automatically removes the entry once the token would have expired anyway.
   *
   * @param token the raw JWT access token string
   * @param expiration the token's expiration date (from its claims)
   */
  public void blacklist(String token, Date expiration) {
    if (token == null || token.isBlank()) {
      return;
    }
    long ttlMillis = expiration.getTime() - System.currentTimeMillis();
    if (ttlMillis <= 0) {
      // Token is already expired; no need to blacklist — it will be rejected anyway.
      return;
    }
    String key = BLACKLIST_KEY_PREFIX + token;
    try {
      redisTemplate
          .opsForValue()
          .set(key, "1", ttlMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
      log.debug("Token blacklisted with TTL {}ms", ttlMillis);
    } catch (Exception e) {
      // If Redis is unavailable, log the error but don't fail the logout.
      // The token will expire naturally; this is an acceptable degradation.
      log.error("Failed to blacklist token in Redis: {}", e.getMessage());
    }
  }

  /**
   * Returns true if the given token has been blacklisted (i.e. the user has logged out).
   *
   * @param token the raw JWT access token string
   */
  public boolean isBlacklisted(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token));
    } catch (Exception e) {
      // If Redis is unavailable, fail open (allow the request) rather than blocking all traffic.
      log.error("Redis unavailable during blacklist check, failing open: {}", e.getMessage());
      return false;
    }
  }
}
