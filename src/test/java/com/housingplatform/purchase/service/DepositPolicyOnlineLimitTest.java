package com.housingplatform.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housingplatform.purchase.config.PurchaseDepositProperties;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DepositPolicyOnlineLimitTest {

  @Test
  void theDepositNeverExceedsOneOnlinePayment() {
    DepositPolicy policy = new DepositPolicy(new PurchaseDepositProperties());
    // 1 % of 8.5 M = 85,000, above Chapa's 75,000 per payment.
    assertThat(policy.amountFor(new BigDecimal("8500000"), Currency.ETB))
        .isEqualByComparingTo("85000.00");
    OnlinePaymentLimits limits = mock(OnlinePaymentLimits.class);
    when(limits.maxFor(Currency.ETB)).thenReturn(new BigDecimal("75000"));
    policy.setOnlineLimits(limits);
    assertThat(policy.amountFor(new BigDecimal("8500000"), Currency.ETB))
        .isEqualByComparingTo("75000.00");
    assertThat(policy.amountFor(new BigDecimal("3200000"), Currency.ETB))
        .isEqualByComparingTo("32000.00");
    // A limit below the minimum deposit wins over the minimum.
    when(limits.maxFor(Currency.ETB)).thenReturn(new BigDecimal("3000"));
    assertThat(policy.amountFor(new BigDecimal("3200000"), Currency.ETB))
        .isEqualByComparingTo("3000.00");
  }
}
