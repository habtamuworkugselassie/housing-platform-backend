package com.housingplatform.purchase.dto;

import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Where to send the buyer to pay. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositCheckoutResponse {
  private String checkoutUrl;
  private String txRef;
  private BigDecimal amount;
  private Currency currency;
  private String provider;
}
