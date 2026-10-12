package com.housingplatform.purchase.service.impl;

import com.housingplatform.identity.domain.User;
import com.housingplatform.payment.chapa.ChapaPaymentMethod;

/** The phone number Chapa's checkout is prefilled with. */
final class PaymentPhones {

  private PaymentPhones() {}

  /**
   * A mobile-money payment goes to the buyer's saved wallet number when they have one; anything
   * else (cards, no preference) keeps the order's contact phone.
   */
  static String forCheckout(User buyer, ChapaPaymentMethod method, String contactPhone) {
    boolean wallet = method != null && method != ChapaPaymentMethod.CARD;
    if (wallet && buyer != null && buyer.getPaymentPhone() != null) {
      return buyer.getPaymentPhone();
    }
    return contactPhone;
  }
}
