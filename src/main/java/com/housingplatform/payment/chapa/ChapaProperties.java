package com.housingplatform.payment.chapa;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Chapa (chapa.co) hosted checkout. Card data never touches this server. */
@Component
@ConfigurationProperties(prefix = "chapa")
@Getter
@Setter
public class ChapaProperties {

  /** Secret API key (CHASECK_TEST-… or CHASECK-…). Empty disables card checkout. */
  private String secretKey = "";

  /** Webhook secret configured in the Chapa dashboard; used to check webhook signatures. */
  private String webhookSecret = "";

  private String baseUrl = "https://api.chapa.co/v1";

  /** Public URL of this API, used to build the webhook callback URL Chapa posts to. */
  private String publicApiBaseUrl = "";

  public boolean isConfigured() {
    return secretKey != null && !secretKey.isBlank();
  }
}
