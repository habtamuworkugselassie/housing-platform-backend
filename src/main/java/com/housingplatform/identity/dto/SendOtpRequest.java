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
@Schema(description = "Request to send an OTP to a phone number")
public class SendOtpRequest {

  @NotBlank(message = "Phone number is required")
  @Schema(description = "User's phone number in E.164 format", example = "+251911234567")
  private String phoneNumber;
}
