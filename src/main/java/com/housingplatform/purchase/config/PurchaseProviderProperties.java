package com.housingplatform.purchase.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The legal entity that operates the platform and is the counterparty to every purchase-order
 * agreement (the buyer signs with the provider, not with the listing company). Defaults name Dream
 * Team PLC; override under {@code purchase.provider.*} in application.yml or the environment.
 */
@Component
@ConfigurationProperties(prefix = "purchase.provider")
@Getter
@Setter
public class PurchaseProviderProperties {

  private String name = "Dream Team PLC";

  private String registrationNumber = "";

  private String address = "Addis Ababa, Ethiopia";

  private String email = "";

  private String phone = "";

  /** Person recorded as signing on the provider's behalf. */
  private String signatoryName = "Authorized Signatory";

  private String signatoryTitle = "General Manager";

  /**
   * When true the provider's signature is applied automatically the moment the buyer signs a
   * standard-form agreement. When false an admin countersigns through the admin endpoint.
   */
  private boolean autoCountersign = true;
}
