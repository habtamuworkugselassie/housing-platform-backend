package com.housingplatform.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class AgentRegistrationRequest {

  @NotNull(message = "Organization ID is required")
  private UUID organizationId;

  private String licenseNumber;
  private String notes;
}
