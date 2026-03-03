package com.housingplatform.exhibition.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExhibitionInterestRequest {

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  private String email;

  @NotBlank(message = "Interest type is required")
  @Pattern(regexp = "exhibitor|visitor", message = "Interest type must be exhibitor or visitor")
  private String interestType;

  private String phoneNumber;

  /** Organization type for the created organization (e.g. REAL_ESTATE_COMPANY, CONTRACTOR). */
  @NotBlank(message = "Organization type is required")
  @Pattern(
      regexp =
          "REAL_ESTATE_COMPANY|SUPPLIER|CONTRACTOR|DEVELOPER|CONSULTANT_ARCHITECT|FINISHING_CONTRACTOR",
      message = "Invalid organization type")
  private String organizationType;

  private String company;
  private String message;
}
