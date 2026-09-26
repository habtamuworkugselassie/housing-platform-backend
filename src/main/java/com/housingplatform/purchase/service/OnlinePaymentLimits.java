package com.housingplatform.purchase.service;

import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.domain.SiteSetting;
import com.housingplatform.shared.repository.SiteSettingRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The most one online (Chapa) payment may be, per currency. Chapa refuses larger amounts at
 * checkout ("amount is greater than the maximum amount supported by the currency"; ETB 75,000 on
 * the test account). Admins set it (site_settings); null means no limit is enforced here.
 */
@Component
@RequiredArgsConstructor
public class OnlinePaymentLimits {

  static final String ETB_KEY = "purchase.online.maxEtb";
  static final String USD_KEY = "purchase.online.maxUsd";

  /** Observed on the Chapa Global test account; admins change it for the live limits. */
  static final BigDecimal DEFAULT_MAX_ETB = new BigDecimal("75000");

  private final SiteSettingRepository settings;

  public record Limits(BigDecimal maxEtb, BigDecimal maxUsd) {}

  @Transactional(readOnly = true)
  public Limits get() {
    return new Limits(read(ETB_KEY, DEFAULT_MAX_ETB), read(USD_KEY, null));
  }

  /** Null for no limit. */
  @Transactional(readOnly = true)
  public BigDecimal maxFor(Currency currency) {
    Limits l = get();
    return currency == Currency.USD ? l.maxUsd() : l.maxEtb();
  }

  /** A zero or empty value removes the limit for that currency. */
  @Transactional
  public Limits set(BigDecimal maxEtb, BigDecimal maxUsd) {
    write(ETB_KEY, maxEtb);
    write(USD_KEY, maxUsd);
    return get();
  }

  private BigDecimal read(String key, BigDecimal fallback) {
    return settings
        .findById(key)
        .map(SiteSetting::getSettingValue)
        .map(
            v -> {
              try {
                BigDecimal b = new BigDecimal(v.trim());
                return b.signum() > 0 ? b : null;
              } catch (NumberFormatException e) {
                return fallback;
              }
            })
        .orElse(fallback);
  }

  private void write(String key, BigDecimal value) {
    SiteSetting s =
        settings.findById(key).orElseGet(() -> SiteSetting.builder().settingKey(key).build());
    // Stored as "0" when cleared so the default does not come back.
    s.setSettingValue(
        value == null || value.signum() <= 0 ? "0" : value.stripTrailingZeros().toPlainString());
    settings.save(s);
  }
}
