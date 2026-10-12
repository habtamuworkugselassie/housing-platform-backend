package com.housingplatform.purchase.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.housingplatform.identity.domain.User;
import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import org.junit.jupiter.api.Test;

class PaymentPhonesTest {

  @Test
  void walletPaymentsUseTheSavedWalletNumberAndEverythingElseTheContactPhone() {
    User buyer = User.builder().paymentPhone("+251922000000").build();

    assertThat(PaymentPhones.forCheckout(buyer, ChapaPaymentMethod.TELEBIRR, "+251911000000"))
        .isEqualTo("+251922000000");
    assertThat(PaymentPhones.forCheckout(buyer, ChapaPaymentMethod.CARD, "+251911000000"))
        .isEqualTo("+251911000000");
    assertThat(PaymentPhones.forCheckout(buyer, null, "+251911000000")).isEqualTo("+251911000000");
    assertThat(PaymentPhones.forCheckout(new User(), ChapaPaymentMethod.MPESA, "+251911000000"))
        .isEqualTo("+251911000000");
  }
}
