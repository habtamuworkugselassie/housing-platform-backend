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

  /**
   * Secret API key (CHASECK_TEST-…/CHASECK-… on chapa.co, CHAPA_TEST_PRIV_… on chapa.global). Empty
   * disables card checkout.
   */
  private String secretKey = "";

  /** Webhook secret configured in the Chapa dashboard; used to check webhook signatures. */
  private String webhookSecret = "";

  /** https://api.chapa.co/v1, or https://api.chapa.global/v1 for Chapa Global accounts. */
  private String baseUrl = "https://api.chapa.co/v1";

  /** Public URL of this API, used to build the webhook callback URL Chapa posts to. */
  private String publicApiBaseUrl = "";

  public boolean isConfigured() {
    return secretKey != null && !secretKey.isBlank();
  }
}
