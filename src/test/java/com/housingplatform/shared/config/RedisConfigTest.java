package com.housingplatform.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.housingplatform.shared.security.RateLimitingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Regression test for the first production start with rate limiting switched on, which failed with
 * "bean 'redisTemplate' ... already defined in RedisAutoConfiguration and overriding is disabled".
 * Bean overriding is off here exactly as SpringApplication sets it.
 */
class RedisConfigTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withAllowBeanDefinitionOverriding(false)
          .withUserConfiguration(RedisConfig.class, RateLimitingFilter.class);

  @Test
  void rateLimitingEnabledStartsWithOneStringRedisTemplate() {
    runner
        .withPropertyValues("rate.limit.auth.enabled=true")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx).hasSingleBean(StringRedisTemplate.class);
              assertThat(ctx).hasSingleBean(RateLimitingFilter.class);
            });
  }

  @Test
  void rateLimitingDisabledWiresNoRedisAtAll() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).doesNotHaveBean(StringRedisTemplate.class);
          assertThat(ctx).doesNotHaveBean(RateLimitingFilter.class);
        });
  }
}
