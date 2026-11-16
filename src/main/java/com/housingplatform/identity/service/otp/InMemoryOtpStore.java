package com.housingplatform.identity.service.otp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Single-instance fallback used when Redis is off ({@code RATE_LIMIT_AUTH_ENABLED=false}). State is
 * lost on restart, which only means users request a new code.
 */
@Component
@ConditionalOnProperty(
    name = "rate.limit.auth.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class InMemoryOtpStore implements OtpStore {

  private record Expiring<T>(T value, Instant expiresAt) {}

  private final Map<String, Expiring<PendingOtp>> codes = new ConcurrentHashMap<>();
  private final Map<String, Expiring<Long>> counters = new ConcurrentHashMap<>();
  private final Clock clock;

  public InMemoryOtpStore() {
    this(Clock.systemUTC());
  }

  public InMemoryOtpStore(Clock clock) {
    this.clock = clock;
  }

  private boolean live(Expiring<?> e) {
    return e != null && clock.instant().isBefore(e.expiresAt());
  }

  @Override
  public void save(String phone, PendingOtp otp, Duration ttl) {
    purge();
    codes.put(phone, new Expiring<>(otp, clock.instant().plus(ttl)));
  }

  @Override
  public Optional<PendingOtp> find(String phone) {
    Expiring<PendingOtp> e = codes.get(phone);
    return live(e) ? Optional.of(e.value()) : Optional.empty();
  }

  @Override
  public int recordFailure(String phone) {
    int[] result = {-1};
    codes.computeIfPresent(
        phone,
        (k, e) -> {
          if (!live(e)) {
            return null;
          }
          PendingOtp p = e.value();
          PendingOtp next =
              new PendingOtp(p.code(), p.reachedPhone(), p.reachedEmail(), p.failedAttempts() + 1);
          result[0] = next.failedAttempts();
          return new Expiring<>(next, e.expiresAt());
        });
    return result[0];
  }

  @Override
  public boolean delete(String phone) {
    Expiring<PendingOtp> removed = codes.remove(phone);
    return live(removed);
  }

  @Override
  public boolean tryAcquire(String key, Duration ttl) {
    boolean[] acquired = {false};
    counters.compute(
        key,
        (k, e) -> {
          if (live(e)) {
            return e;
          }
          acquired[0] = true;
          return new Expiring<>(1L, clock.instant().plus(ttl));
        });
    return acquired[0];
  }

  @Override
  public long increment(String key, Duration ttl) {
    Expiring<Long> next =
        counters.compute(
            key,
            (k, e) ->
                live(e)
                    ? new Expiring<>(e.value() + 1, e.expiresAt())
                    : new Expiring<>(1L, clock.instant().plus(ttl)));
    return next.value();
  }

  private void purge() {
    codes.values().removeIf(e -> !live(e));
    counters.values().removeIf(e -> !live(e));
  }
}
