package com.housingplatform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AgentRegistrationRequest {
    
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;
    
    private String licenseNumber;
    private String notes;
}
