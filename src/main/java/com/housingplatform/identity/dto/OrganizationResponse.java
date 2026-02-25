package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Organization;
import java.time.LocalDateTime;
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
}
