package com.housingplatform.purchase.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Optional free-text accompanying accept, cancel and complete transitions. */
@Data
public class PurchaseOrderDecisionRequest {
  @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
  private String notes;

  @Size(max = 255, message = "Payment reference cannot exceed 255 characters")
  private String paymentReference;
}
