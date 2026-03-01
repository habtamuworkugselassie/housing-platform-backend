package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Organization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class AdminOrganizationCreateRequest {

  @NotBlank(message = "Organization name is required")
  private String name;

  private String registrationNumber;

  @NotNull(message = "Organization type is required")
  private Organization.OrganizationType type;

  private String address;
  private String city;
  private String country;
  private String phoneNumber;

  @jakarta.validation.constraints.Email(message = "Email should be valid")
  private String email;

  private String website;
  private String description;

  private UUID primaryContactUserId;

  /** Initial status when admin creates the organization. Defaults to PENDING_APPROVAL if null. */
  private Organization.OrganizationStatus initialStatus;
}
