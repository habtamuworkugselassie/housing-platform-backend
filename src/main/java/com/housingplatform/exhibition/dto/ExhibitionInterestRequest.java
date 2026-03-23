package com.housingplatform.exhibition.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
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

  /** Organization type for the created organization (any value of Organization.OrganizationType). */
  @NotBlank(message = "Organization type is required")
  @Pattern(
      regexp =
          "BANK|REAL_ESTATE_COMPANY|SUPPLIER|CONTRACTOR|DEVELOPER|INSURANCE|CONSULTANT_ARCHITECT|FINISHING_CONTRACTOR",
      message = "Invalid organization type")
  private String organizationType;

  private String company;
  private String message;

  /** Required when interestType is exhibitor: id of an ACTIVE sponsorship package. */
  private UUID sponsorshipId;
}
