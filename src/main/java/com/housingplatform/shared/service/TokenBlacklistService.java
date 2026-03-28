package com.housingplatform.shared.service;

import java.util.Date;

/**
 * Manages a blacklist of invalidated JWT access tokens.
 *
 * <p>When a user logs out, their current access token is stored with a TTL equal to its remaining
 * validity period. Any subsequent request bearing a blacklisted token is rejected with 401, even if
 * the token's signature and expiry would otherwise be valid.
 */
public interface TokenBlacklistService {

  /**
   * Adds a token to the blacklist. The TTL is set to the token's remaining lifetime so that the
   * cache entry is removed once the token would have expired anyway.
   *
   * @param token the raw JWT access token string
   * @param expiration the token's expiration date (from its claims)
   */
  void blacklist(String token, Date expiration);

  /**
   * Returns true if the given token has been blacklisted (i.e. the user has logged out).
   *
   * @param token the raw JWT access token string
   */
  boolean isBlacklisted(String token);
}
