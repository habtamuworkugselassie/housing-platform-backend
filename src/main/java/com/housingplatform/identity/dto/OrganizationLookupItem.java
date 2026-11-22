package com.housingplatform.identity.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The little a public form needs to pick an organization. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationLookupItem {
  private UUID id;
  private String name;
  private String city;
  private String type;
  private String status;
}
