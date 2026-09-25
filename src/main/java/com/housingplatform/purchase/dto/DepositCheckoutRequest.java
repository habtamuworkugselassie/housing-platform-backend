package com.housingplatform.purchase.dto;

import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import lombok.Data;

/** Optional body of the deposit checkout call. */
@Data
public class DepositCheckoutRequest {
  /** Replaces the method picked when the order was placed; null keeps it. */
  private ChapaPaymentMethod paymentMethod;
}
