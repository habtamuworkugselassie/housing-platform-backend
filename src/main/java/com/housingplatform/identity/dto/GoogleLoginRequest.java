package com.housingplatform.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Sign in or register with a Google ID token from Google Identity Services")
public class GoogleLoginRequest {
  @NotBlank(message = "Google credential is required")
  @Schema(description = "The `credential` (ID token) returned by the Google sign-in button")
  private String idToken;
}
