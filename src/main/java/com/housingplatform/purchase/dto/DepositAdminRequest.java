package com.housingplatform.purchase.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepositAdminRequest {
  @Size(max = 2000)
  private String reason;

  @Size(max = 255)
  private String refundReference;
}
