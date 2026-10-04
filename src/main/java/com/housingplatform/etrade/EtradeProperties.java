package com.housingplatform.etrade;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connection to the standalone <b>etrade-service</b> ({@code services/etrade-service}), which wraps
 * the Ministry of Trade eTrade portal (https://etrade.gov.et). The monolith never calls
 * etrade.gov.et itself; the service owns the upstream quirks (Referer, TLS chain, caching).
 */
@Component
@ConfigurationProperties(prefix = "etrade")
@Getter
@Setter
public class EtradeProperties {

  /** Master switch; when false the lookup endpoints answer 400 without calling the service. */
  private boolean enabled = true;

  /** Base URL of the etrade-service, e.g. {@code http://etrade-service:8085}. */
  private String serviceUrl = "http://localhost:8085";

  /** Sent as {@code X-API-Key}; must be one of the service's {@code ETRADE_SERVICE_API_KEYS}. */
  private String serviceApiKey = "";

  private Duration connectTimeout = Duration.ofSeconds(5);

  /** The service may itself wait up to ~30 s on a slow eTrade; allow for that. */
  private Duration readTimeout = Duration.ofSeconds(40);
}
