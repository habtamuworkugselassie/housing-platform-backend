package com.housingplatform.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.housingplatform.purchase.config.PurchaseDepositProperties;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DepositPolicyTest {

  private final DepositPolicy policy = new DepositPolicy(new PurchaseDepositProperties());

  @Test
  void onePercentOfThePriceWithinTheEtbBand() {
    assertThat(policy.amountFor(new BigDecimal("8500000"), Currency.ETB))
        .isEqualByComparingTo("85000.00");
  }

  @Test
  void clampsToTheMinimumAndMaximumPerCurrency() {
    assertThat(policy.amountFor(new BigDecimal("100000"), Currency.ETB))
        .isEqualByComparingTo("5000.00");
    assertThat(policy.amountFor(new BigDecimal("90000000"), Currency.ETB))
        .isEqualByComparingTo("250000.00");
    assertThat(policy.amountFor(new BigDecimal("5000"), Currency.USD))
        .isEqualByComparingTo("100.00");
    assertThat(policy.amountFor(new BigDecimal("150000"), Currency.USD))
        .isEqualByComparingTo("1500.00");
    assertThat(policy.amountFor(new BigDecimal("900000"), Currency.USD))
        .isEqualByComparingTo("2500.00");
  }

  @Test
  void neverExceedsThePriceItself() {
    assertThat(policy.amountFor(new BigDecimal("3000"), Currency.ETB))
        .isEqualByComparingTo("3000.00");
  }

  @Test
  void honoursConfiguration() {
    PurchaseDepositProperties props = new PurchaseDepositProperties();
    props.setPercent(new BigDecimal("2.5"));
    props.setMaxEtb(new BigDecimal("1000000"));
    props.setDueDays(7);
    props.setEnabled(false);
    DepositPolicy custom = new DepositPolicy(props);
    assertThat(custom.amountFor(new BigDecimal("8500000"), Currency.ETB))
        .isEqualByComparingTo("212500.00");
    assertThat(custom.dueDays()).isEqualTo(7);
    assertThat(custom.isEnabled()).isFalse();
  }
}
