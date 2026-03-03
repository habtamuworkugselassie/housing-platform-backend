package com.housingplatform.property.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class PropertyCreditCoverageRequest {

  @NotNull(message = "Coverage percentage is required")
  @DecimalMin(value = "0.01", message = "Coverage percentage must be greater than 0")
  @DecimalMax(value = "100.00", message = "Coverage percentage cannot exceed 100")
  private BigDecimal coveragePercentage;
}
