package com.housingplatform.etrade.service;

import com.housingplatform.etrade.EtradeProperties;
import com.housingplatform.etrade.client.EtradeClient;
import com.housingplatform.etrade.client.EtradeRegistrationResponse;
import com.housingplatform.shared.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Cached access to the eTrade record for a TIN. Kept as its own bean so the {@code @Cacheable}
 * proxy is actually hit (a self-invocation inside {@link EtradeBusinessService} would bypass it).
 * Misses (unknown TIN) are not cached so a freshly registered trader shows up on the next call.
 */
@Component
@RequiredArgsConstructor
public class EtradeRegistrationLookup {

  public static final String CACHE = "etradeBusinesses";

  private final EtradeProperties properties;
  private final EtradeClient client;

  @Cacheable(cacheNames = CACHE, key = "#tin", unless = "#result == null")
  public Optional<EtradeRegistrationResponse> fetch(String tin) {
    if (!properties.isEnabled()) {
      throw new BusinessException("eTrade business lookup is not enabled on this server");
    }
    return client.findRegistrationByTin(tin);
  }
}
