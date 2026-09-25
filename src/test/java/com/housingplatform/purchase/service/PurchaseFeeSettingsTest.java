package com.housingplatform.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PurchaseFeeSettingsTest {

  @Test
  void vatIsChargedOnPricePlusMarkup() {
    var q =
        PurchaseFeeSettings.quote(
            new BigDecimal("3200000.00"),
            new PurchaseFeeSettings.Rates(new BigDecimal("2"), new BigDecimal("15")));
    assertThat(q.markupAmount()).isEqualByComparingTo("64000.00");
    assertThat(q.vatAmount()).isEqualByComparingTo("489600.00"); // 15 % of 3,264,000
    assertThat(q.total()).isEqualByComparingTo("553600.00");
    assertThat(q.isEmpty()).isFalse();
  }

  @Test
  void noMarkupMeansNoFee() {
    var q =
        PurchaseFeeSettings.quote(
            new BigDecimal("3200000.00"),
            new PurchaseFeeSettings.Rates(BigDecimal.ZERO, new BigDecimal("15")));
    assertThat(q.isEmpty()).isTrue();
  }
}
