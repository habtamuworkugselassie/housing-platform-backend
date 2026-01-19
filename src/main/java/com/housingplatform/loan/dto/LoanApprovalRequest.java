package com.housingplatform.loan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanApprovalRequest {
    
    @NotNull(message = "Approved amount is required")
    private BigDecimal approvedAmount;
    
    private com.housingplatform.shared.domain.Currency approvedCurrency;
    
    @NotNull(message = "Approved interest rate is required")
    private BigDecimal approvedInterestRate;
    
    @NotNull(message = "Approved tenure is required")
    private Integer approvedTenureMonths;
    
    private String approvalNotes;
}
