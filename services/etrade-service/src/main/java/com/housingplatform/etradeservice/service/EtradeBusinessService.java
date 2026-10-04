package com.housingplatform.etradeservice.service;

import com.housingplatform.etradeservice.api.BusinessNotFoundException;
import com.housingplatform.etradeservice.api.InvalidTinException;
import com.housingplatform.etradeservice.client.EtradeClient;
import com.housingplatform.etradeservice.client.EtradeRegistrationResponse;
import com.housingplatform.etradeservice.config.CacheConfig;
import com.housingplatform.etradeservice.config.EtradeProperties;
import com.housingplatform.etradeservice.dto.EtradeBusinessDetails;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** TIN validation, caching of the upstream record, and mapping to the public shape. */
@Service
@RequiredArgsConstructor
public class EtradeBusinessService {

  /** Ethiopian TINs are ten digits. */
  private static final Pattern TIN = Pattern.compile("\\d{10}");

  private final EtradeProperties properties;
  private final EtradeClient client;
  private final EtradeBusinessMapper mapper;
  private final EtradeRegistrationCache cache;

  public EtradeBusinessDetails getBusinessDetails(String tin, boolean includePhotos) {
    String normalized = normalizeTin(tin);
    return cache
        .fetch(normalized)
        .map(r -> mapper.toDetails(r, sourceUrl(normalized), includePhotos))
        .orElseThrow(() -> new BusinessNotFoundException(normalized));
  }

  /** Strips separators and validates the ten-digit shape. */
  public static String normalizeTin(String tin) {
    if (tin == null) {
      throw new InvalidTinException("TIN is required");
    }
    String digits = tin.replaceAll("[\\s\\-/.]", "");
    if (!TIN.matcher(digits).matches()) {
      throw new InvalidTinException("TIN must be exactly 10 digits, e.g. 0081723672");
    }
    return digits;
  }

  private String sourceUrl(String tin) {
    String base = properties.getBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/business-license-checker?tin=" + tin;
  }

  /**
   * Cached upstream access as its own bean so the {@code @Cacheable} proxy is hit (a self-call
   * would bypass it). Misses are not cached so a newly registered trader appears on the next call.
   */
  @Service
  @RequiredArgsConstructor
  public static class EtradeRegistrationCache {
    private final EtradeClient client;

    @Cacheable(cacheNames = CacheConfig.BUSINESSES, key = "#tin", unless = "#result == null")
    public Optional<EtradeRegistrationResponse> fetch(String tin) {
      return client.findRegistrationByTin(tin);
    }
  }
}
