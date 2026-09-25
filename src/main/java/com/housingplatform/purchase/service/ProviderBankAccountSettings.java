package com.housingplatform.purchase.service;

import com.housingplatform.purchase.dto.PurchaseBalanceResponse.BankAccount;
import com.housingplatform.shared.domain.SiteSetting;
import com.housingplatform.shared.repository.SiteSettingRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The provider's bank account buyers transfer the balance to; set by admins (site_settings). */
@Component
@RequiredArgsConstructor
public class ProviderBankAccountSettings {

  static final String BANK = "purchase.bank.name";
  static final String NAME = "purchase.bank.accountName";
  static final String NUMBER = "purchase.bank.accountNumber";
  static final String BRANCH = "purchase.bank.branch";

  private final SiteSettingRepository settings;

  /** Null until at least the bank name and account number are set. */
  @Transactional(readOnly = true)
  public BankAccount get() {
    String bank = value(BANK);
    String number = value(NUMBER);
    if (bank == null || number == null) {
      return null;
    }
    return BankAccount.builder()
        .bankName(bank)
        .accountName(value(NAME))
        .accountNumber(number)
        .branch(value(BRANCH))
        .build();
  }

  @Transactional
  public BankAccount set(BankAccount account) {
    Map<String, String> values =
        Map.of(
            BANK, nz(account.getBankName()),
            NAME, nz(account.getAccountName()),
            NUMBER, nz(account.getAccountNumber()),
            BRANCH, nz(account.getBranch()));
    values.forEach(
        (key, v) -> {
          if (v.isBlank()) {
            settings.deleteById(key);
          } else {
            SiteSetting s =
                settings
                    .findById(key)
                    .orElseGet(() -> SiteSetting.builder().settingKey(key).build());
            s.setSettingValue(v.trim());
            settings.save(s);
          }
        });
    return get();
  }

  private String value(String key) {
    return settings
        .findById(key)
        .map(SiteSetting::getSettingValue)
        .filter(v -> !v.isBlank())
        .orElse(null);
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
