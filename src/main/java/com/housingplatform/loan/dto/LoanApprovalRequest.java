package com.housingplatform.loan.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

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
