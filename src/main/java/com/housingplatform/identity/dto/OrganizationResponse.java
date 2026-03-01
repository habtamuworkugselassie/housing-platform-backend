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
  private String phoneNumber;
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
