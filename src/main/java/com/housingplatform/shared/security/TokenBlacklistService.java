package com.housingplatform.shared.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.housingplatform.shared.config.TokenBlacklistCacheConfig;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Manages a Caffeine-backed blacklist of invalidated JWT access tokens.
 *
 * <p>When a user logs out, their current access token is stored here with a TTL equal to its
 * remaining validity period. Any subsequent request bearing a blacklisted token is rejected with
 * 401, even if the token's signature and expiry would otherwise be valid.
 *
 * <p>Entries expire automatically when the token would have expired, so the blacklist does not grow
 * unbounded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

  @Qualifier(TokenBlacklistCacheConfig.JWT_BLACKLIST_CACHE)
  private final Cache<String, Long> blacklistCache;

  /**
   * Adds a token to the blacklist. The TTL is set to the token's remaining lifetime so that the
   * cache entry is removed once the token would have expired anyway.
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
    try {
      blacklistCache.put(token, expiration.getTime());
      log.debug("Token blacklisted with TTL {}ms", ttlMillis);
    } catch (Exception e) {
      log.error("Failed to blacklist token: {}", e.getMessage());
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
      return blacklistCache.getIfPresent(token) != null;
    } catch (Exception e) {
      log.error("Blacklist check failed, failing open: {}", e.getMessage());
      return false;
    }
  }
}
