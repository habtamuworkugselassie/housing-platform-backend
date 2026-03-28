package com.housingplatform.shared.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.housingplatform.shared.config.TokenBlacklistCacheConfig;
import com.housingplatform.shared.service.TokenBlacklistService;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Caffeine-backed implementation. Entries expire automatically when the token would have expired,
 * so the blacklist does not grow unbounded.
 */
@Slf4j
@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

  private final Cache<String, Long> blacklistCache;

  public TokenBlacklistServiceImpl(
      @Qualifier(TokenBlacklistCacheConfig.JWT_BLACKLIST_CACHE)
          Cache<String, Long> blacklistCache) {
    this.blacklistCache = blacklistCache;
  }

  @Override
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

  @Override
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
