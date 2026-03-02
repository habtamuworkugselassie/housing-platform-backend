package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Organization;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponse {
  private UUID id;
  private String name;
  private String registrationNumber;
  private Organization.OrganizationType type;
  private Organization.OrganizationStatus status;
  private String address;
  private String city;
  private String country;

  /**
   * Phone numbers with country code. For backward compatibility, first phone formatted as single
   * string.
   */
  private List<OrganizationPhoneDto> phoneNumbers;

  /** First phone formatted as "countryCode number" for backward compatibility. */
  public String getPhoneNumber() {
    if (phoneNumbers == null || phoneNumbers.isEmpty()) return null;
    OrganizationPhoneDto first = phoneNumbers.get(0);
    if (first == null || first.getNumber() == null || first.getNumber().isBlank()) return null;
    String cc = first.getCountryCode() != null ? first.getCountryCode().trim() : "";
    String num = first.getNumber().trim();
    return (cc + " " + num).trim();
  }

  private String email;
  private String website;
  private String description;
  private UUID primaryContactUserId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** Logo URL (from media attachment with kind LOGO or primary). */
  private String logoUrl;

  /** All media attachments (logo, images, videos). */
  private List<OrganizationMediaItem> media;
}
