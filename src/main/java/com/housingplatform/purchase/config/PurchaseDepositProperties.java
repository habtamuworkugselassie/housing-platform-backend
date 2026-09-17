package com.housingplatform.purchase.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Reservation deposit the buyer pays to the provider once the seller accepts the order. A
 * percentage of the listed price, clamped per currency. Disabled → orders skip the deposit.
 */
@Component
@ConfigurationProperties(prefix = "purchase.deposit")
@Getter
@Setter
public class PurchaseDepositProperties {

  private boolean enabled = true;

  /** Percent of the listed price, e.g. 1 = 1 %. */
  private BigDecimal percent = new BigDecimal("1");

  private BigDecimal minEtb = new BigDecimal("5000");
  private BigDecimal maxEtb = new BigDecimal("250000");
  private BigDecimal minUsd = new BigDecimal("100");
  private BigDecimal maxUsd = new BigDecimal("2500");

  /** Days after seller acceptance until the deposit is due. */
  private int dueDays = 3;
}
