package com.housingplatform.purchase.service;

import com.housingplatform.purchase.config.PurchaseDepositProperties;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.domain.SiteSetting;
import com.housingplatform.shared.repository.SiteSettingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** How much reservation deposit an order owes: a percentage of the price, clamped per currency. */
@Component
@RequiredArgsConstructor
public class DepositPolicy {

  /** site_settings key for the admin-set ETB per USD rate; wins over the configured one. */
  public static final String ETB_PER_USD_KEY = "purchase.deposit.etbPerUsd";

  private final PurchaseDepositProperties properties;

  private SiteSettingRepository siteSettings;

  @Autowired(required = false)
  public void setSiteSettings(SiteSettingRepository siteSettings) {
    this.siteSettings = siteSettings;
  }

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

  /** Birr per USD for paying an ETB deposit in USD; null when that option is off. */
  public BigDecimal etbPerUsd() {
    BigDecimal rate = adminRate();
    if (rate == null) {
      rate = properties.getEtbPerUsd();
    }
    return rate != null && rate.signum() > 0 ? rate : null;
  }

  /** Sets (or with null clears) the admin rate; the configured one applies when cleared. */
  public void setAdminEtbPerUsd(BigDecimal rate) {
    if (siteSettings == null) {
      throw new IllegalStateException("Site settings are not available");
    }
    if (rate == null) {
      siteSettings.deleteById(ETB_PER_USD_KEY);
      return;
    }
    SiteSetting s =
        siteSettings
            .findById(ETB_PER_USD_KEY)
            .orElseGet(() -> SiteSetting.builder().settingKey(ETB_PER_USD_KEY).build());
    s.setSettingValue(rate.stripTrailingZeros().toPlainString());
    siteSettings.save(s);
  }

  private BigDecimal adminRate() {
    if (siteSettings == null) {
      return null;
    }
    return siteSettings
        .findById(ETB_PER_USD_KEY)
        .map(SiteSetting::getSettingValue)
        .map(
            v -> {
              try {
                return new BigDecimal(v.trim());
              } catch (NumberFormatException e) {
                return null;
              }
            })
        .orElse(null);
  }

  /** The USD amount for an ETB deposit at the configured rate, to the cent (half up). */
  public BigDecimal toUsd(BigDecimal etbAmount) {
    BigDecimal rate = etbPerUsd();
    if (rate == null) {
      throw new IllegalStateException("No ETB/USD rate is configured");
    }
    return etbAmount.divide(rate, 2, RoundingMode.HALF_UP);
  }
}
