package com.housingplatform.identity.dto;

import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** How the buyer usually pays; both fields optional, null clears them. */
@Data
public class PaymentPreferencesRequest {

  private ChapaPaymentMethod preferredPaymentMethod;

  /** Mobile-money number, local (09…) or international form. */
  @Size(max = 32, message = "Phone number is too long")
  private String paymentPhone;
}
