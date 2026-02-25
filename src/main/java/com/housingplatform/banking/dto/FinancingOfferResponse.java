package com.housingplatform.banking.dto;

import com.housingplatform.banking.domain.FinancingOffer;
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
public class FinancingOfferResponse {
  private UUID id;
  private UUID bankId;
  private UUID creditProductId;
  private String creditProductName;
  private String creditProductDescription;
  private BigDecimal interestRate;
  private BigDecimal maxLoanAmount;
  private Integer maxTenureMonths;
  private BigDecimal maxLoanToValueRatio;
  private UUID propertyId;
  private UUID buildingId;
  private UUID projectId;
  private BigDecimal specialInterestRate;
  private BigDecimal specialLTVRatio;
  private String specialTerms;
  private FinancingOffer.FinancingOfferStatus status;
  private String approvalNotes;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
