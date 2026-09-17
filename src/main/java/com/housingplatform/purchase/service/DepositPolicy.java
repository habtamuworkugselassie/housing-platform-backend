package com.housingplatform.purchase.service;

import com.housingplatform.purchase.config.PurchaseDepositProperties;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** How much reservation deposit an order owes: a percentage of the price, clamped per currency. */
@Component
@RequiredArgsConstructor
public class DepositPolicy {

  private final PurchaseDepositProperties properties;

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  public BigDecimal amountFor(BigDecimal listedPrice, Currency currency) {
    BigDecimal raw =
        listedPrice
            .multiply(properties.getPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    BigDecimal min = currency == Currency.USD ? properties.getMinUsd() : properties.getMinEtb();
    BigDecimal max = currency == Currency.USD ? properties.getMaxUsd() : properties.getMaxEtb();
    BigDecimal clamped = raw.max(min).min(max);
    // Never ask for more than the price itself (tiny listings).
    return clamped.min(listedPrice).setScale(2, RoundingMode.HALF_UP);
  }

  public int dueDays() {
    return properties.getDueDays();
  }
}
