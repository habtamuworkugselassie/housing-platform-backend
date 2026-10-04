package com.housingplatform.etradeservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

  public static final String BUSINESSES = "etradeBusinesses";

  @Bean
  public CacheManager cacheManager(EtradeProperties properties) {
    CaffeineCacheManager manager = new CaffeineCacheManager(BUSINESSES);
    manager.setCaffeine(
        Caffeine.newBuilder()
            .expireAfterWrite(properties.getCacheTtl())
            .maximumSize(properties.getCacheMaxSize())
            .recordStats());
    return manager;
  }
}
