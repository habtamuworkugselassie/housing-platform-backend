package com.housingplatform.identity.dto;

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
public class OrganizationMediaItem {
  private UUID id;
  private String url;
  private String caption;
  private Integer displayOrder;
  private Boolean isPrimary;
  private String mediaKind; // IMAGE, VIDEO, LOGO
  private String reviewStatus; // PENDING, APPROVED, REJECTED
  private String reviewNote;
  private UUID organizationId;
  private String organizationName;
  private LocalDateTime createdAt;
}
