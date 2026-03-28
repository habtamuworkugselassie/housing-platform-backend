package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Organization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class AdminOrganizationCreateRequest {

  @NotBlank(message = "Organization name is required")
  private String name;

  private String registrationNumber;

  private String businessRegistration;
  private String license;
  private String vatRegistration;
  private String tinRegistration;
  private String businessRegistrationNumber;
  private String licenseNumber;
  private String vatNumber;
  private String tinNumber;

  @NotNull(message = "Organization type is required")
  private Organization.OrganizationType type;

  private String address;
  private String city;
  private String country;

  private Double latitude;
  private Double longitude;

  /** Phone numbers with country code. If null/empty and legacy phoneNumber is set, that is used. */
  private List<OrganizationPhoneDto> phoneNumbers;

  /**
   * @deprecated Use phoneNumbers. If set and phoneNumbers is empty, converted to one phone.
   */
  @Deprecated private String phoneNumber;

  @jakarta.validation.constraints.Email(message = "Email should be valid")
  private String email;

  private String website;

  private String facebookUrl;
  private String instagramUrl;
  private String linkedinUrl;
  private String twitterUrl;
  private String youtubeUrl;

  private String description;

  private UUID primaryContactUserId;

  /** Initial status when admin creates the organization. Defaults to PENDING_APPROVAL if null. */
  private Organization.OrganizationStatus initialStatus;

  /** Optional material supplier subcategories when {@code type == SUPPLIER}. */
  private List<UUID> supplierSubcategoryIds;
}
