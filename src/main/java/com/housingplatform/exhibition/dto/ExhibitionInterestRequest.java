package com.housingplatform.exhibition.dto;

import com.housingplatform.identity.domain.Sponsorship;
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
  @Pattern(
      regexp = "exhibitor|visitor|partner",
      message = "Interest type must be exhibitor, visitor, or partner")
  private String interestType;

  /** Required for partner interest; ignored for exhibitor and visitor registrations. */
  private Sponsorship.PartnerRole partnerRole;

  /** Required for partner interest: exhibition, platform, or both. */
  private Sponsorship.VisibilityScope visibilityScope;

  /** Required for partner interest: cash, in-kind services, or a hybrid contribution. */
  private Sponsorship.ContributionMode contributionMode;

  private String phoneNumber;

  /**
   * Organization type for the created organization (any value of Organization.OrganizationType).
   */
  @NotBlank(message = "Organization type is required")
  @Pattern(
      regexp =
          "BANK|REAL_ESTATE_COMPANY|SUPPLIER|CONTRACTOR|DEVELOPER|INSURANCE|CONSULTANT_ARCHITECT|FINISHING_CONTRACTOR|MEDIA_COMPANY",
      message = "Invalid organization type")
  private String organizationType;

  private String company;
  private String message;

  /** Required for exhibitors; optional for partners that already know a suitable package. */
  private UUID sponsorshipId;
}
