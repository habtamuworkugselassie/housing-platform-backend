package com.housingplatform.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.Data;

/**
 * Changes the financing split of an order before the seller accepts it, or re-applies after a
 * rejection.
 */
@Data
public class UpdatePurchaseFinancingRequest {

  @DecimalMin(value = "0.01", message = "Financed amount must be greater than 0")
  private BigDecimal financedAmount;

  @DecimalMin(value = "0.00", message = "Down payment cannot be negative")
  private BigDecimal downPaymentAmount;

  @Min(value = 1, message = "Requested tenure must be at least 1 month")
  private Integer requestedTenureMonths;
}
