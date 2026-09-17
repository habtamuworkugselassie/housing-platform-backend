package com.housingplatform.purchase.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * The bank-financing leg of a purchase order. Rate and LTV are snapshots of the catalog entry that
 * was applied, so later edits to the bank's product never change what the buyer was shown.
 */
@Entity
@Table(name = "purchase_order_financing")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PurchaseOrderFinancing extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false, unique = true)
  private PropertyPurchaseOrder purchaseOrder;

  @Column(name = "financing_offer_id", nullable = false)
  private UUID financingOfferId;

  @Column(name = "bank_id", nullable = false)
  private UUID bankId;

  @Column(name = "credit_product_id", nullable = false)
  private UUID creditProductId;

  @Enumerated(EnumType.STRING)
  @Column(name = "offer_level", nullable = false, length = 16)
  private OfferLevel offerLevel;

  @Column(name = "applied_interest_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal appliedInterestRate;

  @Column(name = "applied_ltv_ratio", nullable = false, precision = 5, scale = 2)
  private BigDecimal appliedLtvRatio;

  @Column(name = "min_financeable_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal minFinanceableAmount;

  @Column(name = "max_financeable_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal maxFinanceableAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "financing_mode", nullable = false, length = 16)
  private FinancingMode financingMode;

  @Column(name = "financed_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal financedAmount;

  @Column(name = "cash_portion_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal cashPortionAmount;

  @Column(name = "financing_coverage_ratio", nullable = false, precision = 5, scale = 4)
  private BigDecimal financingCoverageRatio;

  @Column(name = "tenure_months", nullable = false)
  private Integer tenureMonths;

  @Column(name = "estimated_monthly_installment", precision = 19, scale = 2)
  private BigDecimal estimatedMonthlyInstallment;

  @Column(name = "loan_application_id")
  private UUID loanApplicationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "financing_status", nullable = false, length = 32)
  private FinancingStatus financingStatus;

  @Column(name = "approved_amount", precision = 19, scale = 2)
  private BigDecimal approvedAmount;

  @Column(name = "approved_interest_rate", precision = 5, scale = 2)
  private BigDecimal approvedInterestRate;

  @Column(name = "approved_tenure_months")
  private Integer approvedTenureMonths;

  public enum OfferLevel {
    PROPERTY,
    BUILDING
  }

  /** MAXIMUM: finance everything the offer allows. PARTIAL: the buyer chose to finance less. */
  public enum FinancingMode {
    MAXIMUM,
    PARTIAL
  }

  public enum FinancingStatus {
    APPLICATION_SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    PARTIALLY_APPROVED,
    REJECTED,
    DISBURSED,
    WITHDRAWN
  }
}
