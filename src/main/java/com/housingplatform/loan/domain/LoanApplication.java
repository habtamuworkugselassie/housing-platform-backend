package com.housingplatform.loan.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "loan_applications")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LoanApplication extends BaseAuditEntity {

  @Column(name = "buyer_id", nullable = false)
  private UUID buyerId;

  @Column(name = "bank_id", nullable = false)
  private UUID bankId;

  @Column(name = "credit_product_id", nullable = false)
  private UUID creditProductId;

  @Column(name = "financing_offer_id")
  private UUID financingOfferId; // Optional: if applied through a specific offer

  @Column(name = "property_id")
  private UUID propertyId; // Optional: for property-specific loans

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal requestedAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private com.housingplatform.shared.domain.Currency currency =
      com.housingplatform.shared.domain.Currency.ETB;

  @Column(nullable = false)
  private Integer requestedTenureMonths;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LoanApplicationStatus status;

  @Column(columnDefinition = "TEXT")
  private String purpose; // Loan purpose description

  @Column(precision = 19, scale = 2)
  private BigDecimal approvedAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "approved_currency")
  private com.housingplatform.shared.domain.Currency approvedCurrency;

  @Column(precision = 5, scale = 2)
  private BigDecimal approvedInterestRate;

  private Integer approvedTenureMonths;

  @Column(columnDefinition = "TEXT")
  private String rejectionReason;

  @Column(columnDefinition = "TEXT")
  private String approvalNotes;

  @OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<LoanDocument> documents = new ArrayList<>();

  @OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<LoanApplicationStatusHistory> statusHistory = new ArrayList<>();

  public enum LoanApplicationStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    DISBURSED,
    CLOSED
  }
}
