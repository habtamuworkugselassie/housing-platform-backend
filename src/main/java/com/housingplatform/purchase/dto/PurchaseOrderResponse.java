package com.housingplatform.purchase.dto;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {
  private UUID id;
  private String orderNumber;
  private PropertyPurchaseOrder.PurchaseOrderStatus status;
  private PropertyPurchaseOrder.PurchaseType purchaseType;
  private PropertySummary property;
  private BuyerContact buyer;
  private Pricing pricing;

  /** null for cash orders. */
  private FinancingDetails financing;

  private String buyerMessage;
  private LocalDateTime expiresAt;
  private String cancellationReason;
  private String rejectionReason;
  private String paymentReference;
  private List<String> warnings;

  /** Agreements between buyer and provider, without content; fetch one by id for the text. */
  private List<PurchaseAgreementResponse> agreements;

  /** Agreements still waiting for the buyer's signature. */
  private Integer pendingSignatures;

  /** Reservation deposit owed to the provider; null before seller acceptance or when disabled. */
  private PurchaseDepositResponse deposit;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<StatusHistoryEntry> statusHistory;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PropertySummary {
    private UUID id;
    private String title;
    private String city;
    private String unitNumber;
    private UUID realEstateCompanyId;
    private String realEstateCompanyName;
    private UUID agentId;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class BuyerContact {
    private UUID id;
    private String fullName;
    private String contactPhone;
    private String contactEmail;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Pricing {
    private BigDecimal listedPrice;
    private Currency currency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FinancingDetails {
    private PurchaseOrderFinancing.FinancingStatus financingStatus;
    private UUID financingOfferId;
    private PurchaseOrderFinancing.OfferLevel offerLevel;
    private UUID bankId;
    private String bankName;
    private UUID creditProductId;
    private String creditProductName;
    private BigDecimal appliedInterestRate;
    private BigDecimal appliedLtvRatio;
    private BigDecimal minFinanceableAmount;
    private BigDecimal maxFinanceableAmount;
    private PurchaseOrderFinancing.FinancingMode financingMode;
    private BigDecimal financedAmount;
    private BigDecimal cashPortionAmount;
    private BigDecimal financingCoverageRatio;
    private Integer tenureMonths;
    private BigDecimal estimatedMonthlyInstallment;
    private UUID loanApplicationId;
    private BigDecimal approvedAmount;
    private BigDecimal approvedInterestRate;
    private Integer approvedTenureMonths;

    /** Cash the buyer would have to bring if they accept a partial approval. */
    private BigDecimal proposedCashPortionAmount;

    private List<String> nextSteps;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StatusHistoryEntry {
    private PropertyPurchaseOrder.PurchaseOrderStatus fromStatus;
    private PropertyPurchaseOrder.PurchaseOrderStatus toStatus;
    private String changedBy;
    private LocalDateTime changedAt;
    private String notes;
  }
}
