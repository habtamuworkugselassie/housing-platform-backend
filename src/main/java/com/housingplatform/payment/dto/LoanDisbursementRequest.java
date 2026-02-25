package com.housingplatform.payment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class LoanDisbursementRequest {

  @NotNull(message = "Loan application ID is required")
  private UUID loanApplicationId;

  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
  private BigDecimal amount;

  @NotBlank(message = "Currency is required")
  private String currency;

  private String description;
}
