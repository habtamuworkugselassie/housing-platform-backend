package com.housingplatform.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to verify an OTP sent to a phone number")
public class VerifyOtpRequest {

  @NotBlank(message = "Phone number is required")
  @Schema(description = "User's phone number in E.164 format", example = "+251911234567")
  private String phoneNumber;

  @NotBlank(message = "OTP code is required")
  @Schema(description = "6-digit OTP code", example = "123456")
  private String code;
}
