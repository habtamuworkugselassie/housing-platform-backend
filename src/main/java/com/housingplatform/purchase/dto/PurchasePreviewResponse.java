package com.housingplatform.purchase.dto;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What a purchase order on this property would look like, computed without side effects. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchasePreviewResponse {
  private UUID propertyId;
  private BigDecimal listedPrice;
  private Currency currency;

  /** The type an order would get with {@code useFinancing} left unset. */
  private PropertyPurchaseOrder.PurchaseType purchaseType;

  private boolean financingAvailable;
  private List<FinancingOption> financingOffers;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FinancingOption {
    private UUID financingOfferId;
    private UUID bankId;
    private String bankName;
    private UUID creditProductId;
    private String creditProductName;
    private PurchaseOrderFinancing.OfferLevel offerLevel;
    private BigDecimal interestRate;
    private BigDecimal ltvRatio;
    private Integer minTenureMonths;
    private Integer maxTenureMonths;
    private BigDecimal minFinanceableAmount;
    private BigDecimal maxFinanceableAmount;
    private BigDecimal minimumDownPayment;
    private boolean partialFinancingAllowed;
    private boolean recommended;
  }
}
