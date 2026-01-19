package com.housingplatform.identity.api;

import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.dto.AgentRegistrationRequest;
import com.housingplatform.identity.dto.AgentResponse;
import com.housingplatform.identity.dto.CreateAgentRequest;
import com.housingplatform.identity.dto.UpdateAgentRequest;
import com.housingplatform.identity.service.RealEstateAgentService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import com.housingplatform.shared.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/real-estate-agents")
@Tag(name = "Real Estate Agents", description = "Real estate agent management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class RealEstateAgentController {
    
    private final RealEstateAgentService agentService;
    
    @PostMapping("/register")
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @AuthActionScope("agents.register")
    @Operation(summary = "Register as real estate agent", description = "Register the current user as a real estate agent linked to a company")
    public ResponseEntity<AgentResponse> registerAgent(@Valid @RequestBody AgentRegistrationRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        AgentResponse agent = agentService.registerAgent(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(agent);
    }
    
    @GetMapping("/me")
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @Operation(summary = "Get current agent profile", description = "Get the current user's agent profile")
    public ResponseEntity<AgentResponse> getCurrentAgent() {
        UUID userId = UserContext.getCurrentUserId();
        AgentResponse agent = agentService.getAgentByUserId(userId);
        return ResponseEntity.ok(agent);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get agent by ID", description = "Get agent information by ID")
    public ResponseEntity<AgentResponse> getAgentById(@PathVariable UUID id) {
        AgentResponse agent = agentService.getAgentById(id);
        return ResponseEntity.ok(agent);
    }
    
    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get agents by organization", description = "Get all agents for a specific organization")
    public ResponseEntity<List<AgentResponse>> getAgentsByOrganization(@PathVariable UUID organizationId) {
        List<AgentResponse> agents = agentService.getAgentsByOrganizationId(organizationId);
        return ResponseEntity.ok(agents);
    }
    
    @PostMapping("/register-for-user/{userId}")
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @AuthActionScope("agents.register-other")
    @Operation(summary = "Register another user as agent", description = "Super agents can register other REALTOR users as agents for their organization")
    public ResponseEntity<AgentResponse> registerAgentForUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AgentRegistrationRequest request) {
        AgentResponse agent = agentService.registerAgent(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(agent);
    }
    
    @PostMapping("/create")
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @AuthActionScope("agents.create")
    @Operation(summary = "Create new agent", description = "Super agents can create a new user account and register them as an agent for their organization")
    public ResponseEntity<AgentResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        AgentResponse agent = agentService.createAgentForOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(agent);
    }
    
    @PutMapping("/{id}")
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @AuthActionScope("agents.update")
    @Operation(summary = "Update agent", description = "Super agents can update agent details in their organization")
    public ResponseEntity<AgentResponse> updateAgent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAgentRequest request) {
        AgentResponse agent = agentService.updateAgent(id, request);
        return ResponseEntity.ok(agent);
    }
    
    @PutMapping("/{id}/status")
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @AuthActionScope("agents.update-status")
    @Operation(summary = "Update agent status", description = "Update agent status (admin only)")
    public ResponseEntity<AgentResponse> updateAgentStatus(
            @PathVariable UUID id,
            @RequestParam RealEstateAgent.AgentStatus status) {
        AgentResponse agent = agentService.updateAgentStatus(id, status);
        return ResponseEntity.ok(agent);
    }
}
