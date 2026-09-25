package com.housingplatform.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housingplatform.purchase.config.PurchaseDepositProperties;
import com.housingplatform.shared.domain.SiteSetting;
import com.housingplatform.shared.repository.SiteSettingRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DepositPolicyRateTest {

  @Test
  void theAdminRateWinsOverTheConfiguredOneAndGarbageIsIgnored() {
    PurchaseDepositProperties props = new PurchaseDepositProperties();
    props.setEtbPerUsd(new BigDecimal("140"));
    DepositPolicy policy = new DepositPolicy(props);
    assertThat(policy.etbPerUsd()).isEqualByComparingTo("140");

    SiteSettingRepository repo = mock(SiteSettingRepository.class);
    policy.setSiteSettings(repo);
    when(repo.findById(DepositPolicy.ETB_PER_USD_KEY))
        .thenReturn(Optional.of(SiteSetting.builder().settingValue("155.5").build()));
    assertThat(policy.etbPerUsd()).isEqualByComparingTo("155.5");
    assertThat(policy.toUsd(new BigDecimal("85000"))).isEqualByComparingTo("546.62");

    when(repo.findById(DepositPolicy.ETB_PER_USD_KEY))
        .thenReturn(Optional.of(SiteSetting.builder().settingValue("abc").build()));
    assertThat(policy.etbPerUsd()).isEqualByComparingTo("140");
  }

  @Test
  void noRateMeansUsdIsOff() {
    assertThat(new DepositPolicy(new PurchaseDepositProperties()).etbPerUsd()).isNull();
  }
}
