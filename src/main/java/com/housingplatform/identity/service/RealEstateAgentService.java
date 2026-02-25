package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.dto.AgentRegistrationRequest;
import com.housingplatform.identity.dto.AgentResponse;
import com.housingplatform.identity.dto.CreateAgentRequest;
import com.housingplatform.identity.dto.UpdateAgentRequest;
import java.util.List;
import java.util.UUID;

public interface RealEstateAgentService {
  AgentResponse registerAgent(UUID userId, AgentRegistrationRequest request);

  AgentResponse createAgentForOrganization(CreateAgentRequest request);

  AgentResponse getAgentByUserId(UUID userId);

  AgentResponse getAgentById(UUID agentId);

  List<AgentResponse> getAgentsByOrganizationId(UUID organizationId);

  AgentResponse updateAgentStatus(UUID agentId, RealEstateAgent.AgentStatus status);

  AgentResponse updateAgent(UUID agentId, UpdateAgentRequest request);

  void validateAgentCanManageProperty(UUID agentId, UUID propertyCompanyId);
}
