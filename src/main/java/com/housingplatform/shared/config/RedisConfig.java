package com.housingplatform.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redis is only needed for auth rate limiting, so {@link RedisAutoConfiguration} is excluded in
 * {@code HousingPlatformApplication} and pulled back in here, behind the same property that enables
 * {@code RateLimitingFilter}.
 *
 * <p>This class deliberately defines no beans. The auto-configuration it imports already provides
 * {@code redisTemplate} and {@code stringRedisTemplate}; redefining {@code redisTemplate} here
 * collided with that one under the same name, and because bean overriding is off by default the
 * application refused to start the first time rate limiting was switched on.
 */
@Configuration
@ConditionalOnProperty(
    name = "rate.limit.auth.enabled",
    havingValue = "true",
    matchIfMissing = false)
@Import(RedisAutoConfiguration.class)
public class RedisConfig {}
