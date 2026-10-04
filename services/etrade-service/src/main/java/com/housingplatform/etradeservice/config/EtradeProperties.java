package com.housingplatform.etradeservice.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ethiopian Ministry of Trade "eTrade" portal (https://etrade.gov.et). The public business license
 * checker page is backed by an unauthenticated JSON API that returns a trader's registration and
 * licensed businesses by Tax Identification Number (TIN).
 */
@ConfigurationProperties(prefix = "etrade")
@Getter
@Setter
public class EtradeProperties {

  /**
   * Shared secrets callers must present in the {@code X-API-Key} header. Empty list = open (only
   * sensible on a private network). Several keys allow rotation without downtime.
   */
  private List<String> apiKeys = new ArrayList<>();

  /** How long a successful lookup is served from memory before eTrade is asked again. */
  private Duration cacheTtl = Duration.ofHours(6);

  /** Upper bound on cached TINs. */
  private long cacheMaxSize = 5000;

  /** Origin of the eTrade portal. The API lives under {@code /api}. */
  private String baseUrl = "https://etrade.gov.et";

  /**
   * eTrade rejects API calls with HTTP 417 "Invalid referer header" unless the Referer points at
   * one of its own pages, so every request carries this value.
   */
  private String referer = "https://etrade.gov.et/business-license-checker";

  /** Sent as the User-Agent header. */
  private String userAgent = "EthioBuildConnect/1.0 (+https://ethiobuildconnect.et)";

  /**
   * Language segment of the API path. "en" returns Gregorian dates (M/d/yyyy); "am" returns the
   * same record with Ethiopian-calendar dates, which the mapper cannot parse.
   */
  private String language = "en";

  private Duration connectTimeout = Duration.ofSeconds(10);

  private Duration readTimeout = Duration.ofSeconds(30);

  /**
   * etrade.gov.et serves its leaf certificate without the GlobalSign intermediate, so a stock JDK
   * trust store cannot validate the chain. When true, the bundled intermediate (see {@code
   * classpath:etrade/globalsign-gcc-r3-ev-tls-ca-2025.pem}) is added to the trusted certificates
   * for this client only. Set false if the server starts sending a complete chain.
   */
  private boolean trustBundledIntermediate = true;

  /** Classpath or file location of extra PEM certificates to trust (one or more in one file). */
  private String intermediateCertificate = "classpath:etrade/globalsign-gcc-r3-ev-tls-ca-2025.pem";
}
