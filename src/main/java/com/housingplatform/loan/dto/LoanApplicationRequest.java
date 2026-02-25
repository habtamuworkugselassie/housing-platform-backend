package com.housingplatform.loan.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class LoanApplicationRequest {

  @NotNull(message = "Bank ID is required")
  private UUID bankId;

  @NotNull(message = "Credit product ID is required")
  private UUID creditProductId;

  private UUID financingOfferId;
  private UUID propertyId;

  @NotNull(message = "Requested amount is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "Requested amount must be greater than 0")
  private BigDecimal requestedAmount;

  @NotNull(message = "Currency is required")
  private com.housingplatform.shared.domain.Currency currency;

  @NotNull(message = "Requested tenure is required")
  @Min(value = 1, message = "Requested tenure must be at least 1 month")
  private Integer requestedTenureMonths;

  private String purpose;
}
