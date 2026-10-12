package com.housingplatform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** A signed-in user sets or changes their own password. */
@Data
public class ChangePasswordRequest {

  /** Required when the account already has a password; ignored for a passwordless one. */
  private String currentPassword;

  @NotBlank(message = "New password is required")
  @Size(min = 8, message = "Password must be at least 8 characters long")
  @Pattern(
      regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
      message =
          "Password must contain at least one uppercase letter, one lowercase letter, and one number")
  private String newPassword;
}
