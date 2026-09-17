package com.housingplatform.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectPurchaseOrderRequest {
  @NotBlank(message = "A rejection reason is required")
  @Size(max = 2000, message = "Reason cannot exceed 2000 characters")
  private String reason;
}
