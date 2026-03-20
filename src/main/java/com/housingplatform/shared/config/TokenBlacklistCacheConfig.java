package com.housingplatform.shared.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-memory JWT blacklist using Caffeine. Entries expire when the token would have expired, so the
 * set stays bounded without a separate store.
 */
@Configuration
public class TokenBlacklistCacheConfig {

  public static final String JWT_BLACKLIST_CACHE = "jwtBlacklistCache";

  @Bean(JWT_BLACKLIST_CACHE)
  public Cache<String, Long> jwtBlacklistCache() {
    return Caffeine.newBuilder()
        .maximumSize(500_000)
        .expireAfter(
            new Expiry<String, Long>() {
              @Override
              public long expireAfterCreate(String key, Long expirationEpochMs, long currentTime) {
                return ttlNanos(expirationEpochMs);
              }

              @Override
              public long expireAfterUpdate(
                  String key, Long expirationEpochMs, long currentTime, long currentDuration) {
                return ttlNanos(expirationEpochMs);
              }

              @Override
              public long expireAfterRead(
                  String key, Long expirationEpochMs, long currentTime, long currentDuration) {
                return currentDuration;
              }

              private long ttlNanos(long expirationEpochMs) {
                long ms = expirationEpochMs - System.currentTimeMillis();
                return ms > 0 ? TimeUnit.MILLISECONDS.toNanos(ms) : 0L;
              }
            })
        .build();
  }
}
