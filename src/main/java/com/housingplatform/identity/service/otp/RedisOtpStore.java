package com.housingplatform.identity.service.otp;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Production store. Redis is enabled together with auth rate limiting (see {@code RedisConfig}), so
 * both are switched on by {@code RATE_LIMIT_AUTH_ENABLED=true}.
 */
@Component
@ConditionalOnProperty(name = "rate.limit.auth.enabled", havingValue = "true")
public class RedisOtpStore implements OtpStore {

  private static final String CODE_PREFIX = "otp:code:";
  private static final String COUNTER_PREFIX = "otp:limit:";

  private final StringRedisTemplate redis;

  public RedisOtpStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void save(String phone, PendingOtp otp, Duration ttl) {
    String key = CODE_PREFIX + phone;
    redis.delete(key);
    redis
        .opsForHash()
        .putAll(
            key,
            Map.of(
                "code", otp.code(),
                "phone", Boolean.toString(otp.reachedPhone()),
                "email", Boolean.toString(otp.reachedEmail()),
                "failed", Integer.toString(otp.failedAttempts())));
    redis.expire(key, ttl);
  }

  @Override
  public Optional<PendingOtp> find(String phone) {
    Map<Object, Object> h = redis.opsForHash().entries(CODE_PREFIX + phone);
    if (h == null || h.get("code") == null) {
      return Optional.empty();
    }
    return Optional.of(
        new PendingOtp(
            (String) h.get("code"),
            Boolean.parseBoolean((String) h.get("phone")),
            Boolean.parseBoolean((String) h.get("email")),
            Integer.parseInt((String) h.getOrDefault("failed", "0"))));
  }

  @Override
  public int recordFailure(String phone) {
    String key = CODE_PREFIX + phone;
    if (!Boolean.TRUE.equals(redis.hasKey(key))) {
      return -1;
    }
    Long failed = redis.opsForHash().increment(key, "failed", 1);
    Long ttl = redis.getExpire(key);
    if (ttl != null && ttl == -1) {
      // The code expired between the check and the increment, leaving a stray hash without a TTL.
      redis.delete(key);
      return -1;
    }
    return failed == null ? -1 : failed.intValue();
  }

  @Override
  public boolean delete(String phone) {
    return Boolean.TRUE.equals(redis.delete(CODE_PREFIX + phone));
  }

  @Override
  public boolean tryAcquire(String key, Duration ttl) {
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(COUNTER_PREFIX + key, "1", ttl));
  }

  @Override
  public long increment(String key, Duration ttl) {
    String k = COUNTER_PREFIX + key;
    Long value = redis.opsForValue().increment(k);
    if (value != null && value == 1L) {
      redis.expire(k, ttl);
    }
    return value == null ? 0 : value;
  }
}
