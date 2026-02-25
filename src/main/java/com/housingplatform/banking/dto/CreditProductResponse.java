package com.housingplatform.banking.dto;

import com.housingplatform.banking.domain.CreditProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditProductResponse {
  private UUID id;
  private UUID bankId;
  private String name;
  private String description;
  private CreditProduct.CreditProductType productType;
  private BigDecimal interestRate;
  private Integer minTenureMonths;
  private Integer maxTenureMonths;
  private BigDecimal maxLoanToValueRatio;
  private BigDecimal minLoanAmount;
  private BigDecimal maxLoanAmount;
  private com.housingplatform.shared.domain.Currency currency;
  private String eligibilityCriteria;
  private CreditProduct.CreditProductStatus status;
  private BigDecimal processingFee;
  private BigDecimal prepaymentPenalty;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
