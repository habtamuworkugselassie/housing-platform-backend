package com.housingplatform.payment.chapa;

import java.util.List;

/**
 * Methods a buyer can pick on our side before being sent to Chapa's hosted checkout. The code is
 * what Chapa Global accepts in {@code preferred_payment_methods} (verified against its API; unknown
 * names are rejected with "Invalid Preferred Payment Method Format"). Chapa has no code for cards,
 * so {@link #CARD} sends no preference and the checkout shows every enabled method.
 */
public enum ChapaPaymentMethod {
  TELEBIRR("telebirr"),
  CBE_BIRR("cbebirr"),
  MPESA("mpesa"),
  AWASH_BIRR("awashbirr"),
  CARD(null);

  private final String chapaCode;

  ChapaPaymentMethod(String chapaCode) {
    this.chapaCode = chapaCode;
  }

  /** The {@code preferred_payment_methods} value for this choice; empty means "show all". */
  public List<String> preferredPaymentMethods() {
    return chapaCode == null ? List.of() : List.of(chapaCode);
  }
}
