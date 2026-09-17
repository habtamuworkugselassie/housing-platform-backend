package com.housingplatform.purchase.dto;

import com.housingplatform.purchase.domain.PurchaseDeposit;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseDepositResponse {
  private BigDecimal amount;
  private Currency currency;
  private PurchaseDeposit.DepositStatus status;
  private LocalDateTime dueAt;
  private String provider;
  private String txRef;

  /** Present while a checkout is pending so the client can resume it. */
  private String checkoutUrl;

  private String providerReference;
  private String paymentMethod;
  private LocalDateTime paidAt;
  private String failureReason;
  private Integer attempts;

  /** True when the buyer still has to sign the deposit terms before paying. */
  private Boolean termsPending;

  /** True when card checkout is available on this server. */
  private Boolean checkoutAvailable;

  private String refundReference;
  private LocalDateTime refundedAt;
  private String waiveReason;
}
