package com.housingplatform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Super admin sets a company account's password directly (no email round-trip). */
@Data
public class SetAccountPasswordRequest {

  @NotBlank(message = "Password is required")
  @Size(min = 8, message = "Password must be at least 8 characters long")
  @Pattern(
      regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
      message =
          "Password must contain at least one uppercase letter, one lowercase letter, and one number")
  private String password;
}
