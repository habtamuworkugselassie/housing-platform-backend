package com.housingplatform.purchase.service;

import com.housingplatform.shared.domain.SiteSetting;
import com.housingplatform.shared.repository.SiteSettingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The provider's markup and the VAT charged on top of the price, set by admins (site_settings).
 * Buyers pay markup = price x markup %, and VAT = (price + markup) x VAT %, as one service-fee
 * payment once the seller accepts. The amounts are fixed on each order when it is placed.
 */
@Component
@RequiredArgsConstructor
public class PurchaseFeeSettings {

  static final String MARKUP_KEY = "purchase.fees.markupPercent";
  static final String VAT_KEY = "purchase.fees.vatRate";

  /** Ethiopian standard VAT rate; admins can change it. */
  static final BigDecimal DEFAULT_VAT = new BigDecimal("15");

  private final SiteSettingRepository settings;

  public record Rates(BigDecimal markupPercent, BigDecimal vatRate) {}

  /** What an order on {@code price} would owe; all zero when no markup and no VAT apply. */
  public record Quote(
      BigDecimal markupPercent,
      BigDecimal markupAmount,
      BigDecimal vatRate,
      BigDecimal vatAmount,
      BigDecimal total) {
    public boolean isEmpty() {
      return total.signum() == 0;
    }
  }

  @Transactional(readOnly = true)
  public Rates rates() {
    return new Rates(number(MARKUP_KEY, BigDecimal.ZERO), number(VAT_KEY, DEFAULT_VAT));
  }

  @Transactional
  public Rates set(BigDecimal markupPercent, BigDecimal vatRate) {
    put(MARKUP_KEY, markupPercent);
    put(VAT_KEY, vatRate);
    return rates();
  }

  @Transactional(readOnly = true)
  public Quote quote(BigDecimal price) {
    return quote(price, rates());
  }

  /** No markup means no fee at all: VAT is only collected together with the provider's markup. */
  public static Quote quote(BigDecimal price, Rates rates) {
    BigDecimal pct = nz(rates.markupPercent());
    BigDecimal rate = nz(rates.vatRate());
    if (price == null || pct.signum() <= 0) {
      return new Quote(
          pct,
          BigDecimal.ZERO.setScale(2),
          rate,
          BigDecimal.ZERO.setScale(2),
          BigDecimal.ZERO.setScale(2));
    }
    BigDecimal markup =
        price.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    BigDecimal vat =
        price.add(markup).multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    return new Quote(pct, markup, rate, vat, markup.add(vat));
  }

  private BigDecimal number(String key, BigDecimal fallback) {
    return settings
        .findById(key)
        .map(SiteSetting::getSettingValue)
        .map(
            v -> {
              try {
                return new BigDecimal(v.trim());
              } catch (NumberFormatException e) {
                return null;
              }
            })
        .orElse(fallback);
  }

  private void put(String key, BigDecimal value) {
    if (value == null) {
      return;
    }
    if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new com.housingplatform.shared.exception.BusinessException(
          "Rates must be between 0 and 100 percent");
    }
    SiteSetting s =
        settings.findById(key).orElseGet(() -> SiteSetting.builder().settingKey(key).build());
    s.setSettingValue(value.stripTrailingZeros().toPlainString());
    settings.save(s);
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
