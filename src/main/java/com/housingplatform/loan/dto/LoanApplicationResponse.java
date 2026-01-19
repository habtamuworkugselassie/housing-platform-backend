package com.housingplatform.loan.dto;

import com.housingplatform.loan.domain.LoanApplication;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationResponse {
    private UUID id;
    private UUID buyerId;
    private UUID bankId;
    private UUID creditProductId;
    private UUID financingOfferId;
    private UUID propertyId;
    private BigDecimal requestedAmount;
    private com.housingplatform.shared.domain.Currency currency;
    private Integer requestedTenureMonths;
    private LoanApplication.LoanApplicationStatus status;
    private String purpose;
    private BigDecimal approvedAmount;
    private com.housingplatform.shared.domain.Currency approvedCurrency;
    private BigDecimal approvedInterestRate;
    private Integer approvedTenureMonths;
    private String rejectionReason;
    private String approvalNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
