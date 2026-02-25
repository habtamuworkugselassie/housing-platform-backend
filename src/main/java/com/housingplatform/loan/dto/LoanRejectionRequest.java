package com.housingplatform.loan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoanRejectionRequest {

  @NotBlank(message = "Rejection reason is required")
  private String rejectionReason;
}
