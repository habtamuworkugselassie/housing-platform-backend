package com.housingplatform.banking.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "credit_products")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CreditProduct extends BaseAuditEntity {

  @Column(name = "bank_id", nullable = false)
  private UUID bankId;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CreditProductType productType;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal interestRate; // Annual percentage rate

  @Column(nullable = false)
  private Integer minTenureMonths;

  @Column(nullable = false)
  private Integer maxTenureMonths;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal maxLoanToValueRatio; // LTV ratio (e.g., 0.80 for 80%)

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal minLoanAmount;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal maxLoanAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private com.housingplatform.shared.domain.Currency currency =
      com.housingplatform.shared.domain.Currency.ETB;

  @Column(columnDefinition = "TEXT")
  private String eligibilityCriteria; // JSON or text description

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CreditProductStatus status;

  @Column(precision = 19, scale = 2)
  private BigDecimal processingFee;

  @Column(precision = 19, scale = 2)
  private BigDecimal prepaymentPenalty;

  public enum CreditProductType {
    HOME_PURCHASE,
    CONSTRUCTION_LOAN,
    MATERIAL_FINANCING,
    REFINANCING
  }

  public enum CreditProductStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING_APPROVAL
  }
}
