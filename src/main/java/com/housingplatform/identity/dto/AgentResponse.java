package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.RealEstateAgent;
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
public class AgentResponse {
  private UUID id;
  private UUID userId;
  private UserResponse user;
  private UUID organizationId;
  private OrganizationResponse organization;
  private RealEstateAgent.AgentStatus status;
  private Boolean isSuperAgent;
  private String licenseNumber;
  private String notes;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
