package com.housingplatform.identity.service.otp;

import java.time.Duration;
import java.util.Optional;

/**
 * Short-lived state behind one-time codes: the outstanding code per phone number plus the counters
 * that throttle sending. Redis in production (shared across instances, survives restarts), memory
 * when Redis is not enabled (local development, tests).
 */
public interface OtpStore {

  /**
   * An outstanding code and the channels it actually reached. Only a code that reached the phone
   * (SMS / WhatsApp) proves the phone number; an emailed code proves the email address.
   */
  record PendingOtp(String code, boolean reachedPhone, boolean reachedEmail, int failedAttempts) {}

  void save(String phone, PendingOtp otp, Duration ttl);

  Optional<PendingOtp> find(String phone);

  /** Records one wrong guess; returns the total so far, or -1 when no code is outstanding. */
  int recordFailure(String phone);

  /** Removes the code; true only for the caller that actually removed it (single use). */
  boolean delete(String phone);

  /** Sets {@code key} if absent for {@code ttl}; true when this call set it. */
  boolean tryAcquire(String key, Duration ttl);

  /** Increments a counter that expires {@code ttl} after its first increment. */
  long increment(String key, Duration ttl);
}
