package com.housingplatform.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The smallest account a buyer can open: a name and a reachable phone number. Everything else is
 * optional. Used from the purchase-order flow so a visitor can order without leaving the page.
 */
@Data
@Schema(
    description = "Minimal buyer registration: full name and phone; email and password optional")
public class QuickRegistrationRequest {

  @NotBlank(message = "Full name is required")
  @Size(max = 255, message = "Full name is too long")
  @Schema(example = "Abebe Kebede")
  private String fullName;

  @NotBlank(message = "Phone number is required")
  @Size(max = 32, message = "Phone number is too long")
  @Schema(description = "E.164 or Ethiopian local form", example = "0911223344")
  private String phoneNumber;

  @Email(message = "Email should be valid")
  @Size(max = 255, message = "Email is too long")
  @Schema(description = "Optional", example = "abebe@example.com")
  private String email;

  /** Optional. Without one the account is passwordless and signs in with a WhatsApp code. */
  @Size(min = 8, message = "Password must be at least 8 characters long")
  @Pattern(
      regexp = "^$|^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
      message =
          "Password must contain at least one uppercase letter, one lowercase letter, and one number")
  @Schema(description = "Optional; omit for a passwordless (WhatsApp code) account")
  private String password;
}
