package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.RealEstateAgent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAgentRequest {
    private String licenseNumber;
    private String notes;
    private RealEstateAgent.AgentStatus status;
}
