package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Organization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class OrganizationRequest {

  @NotBlank(message = "Organization name is required")
  private String name;

  private String registrationNumber;

  @NotNull(message = "Organization type is required")
  private Organization.OrganizationType type;

  private String address;
  private String city;
  private String country;

  /** One or more phone numbers with country code. Empty numbers are ignored. */
  private List<OrganizationPhoneDto> phoneNumbers;

  @jakarta.validation.constraints.Email(message = "Email should be valid")
  private String email;

  private String website;
  private String description;

  private UUID primaryContactUserId;
}
