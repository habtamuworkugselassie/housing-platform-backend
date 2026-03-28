package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.AgentRegistrationRequest;
import com.housingplatform.identity.dto.AgentResponse;
import com.housingplatform.identity.dto.CreateAgentRequest;
import com.housingplatform.identity.dto.UpdateAgentRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.RealEstateAgentMapper;
import com.housingplatform.identity.service.RealEstateAgentService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RealEstateAgentServiceImpl implements RealEstateAgentService {

  private final RealEstateAgentRepository agentRepository;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final RealEstateAgentMapper agentMapper;
  private final PasswordEncoder passwordEncoder;

  @Override
  public AgentResponse registerAgent(UUID userId, AgentRegistrationRequest request) {
    // Check if user already has an agent profile
    if (agentRepository.existsByUserId(userId)) {
      throw new BusinessException("User is already registered as a real estate agent");
    }

    // Get user and verify they have REALTOR role
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

    if (!user.getRoles().contains(User.UserRole.REALTOR)) {
      throw new BusinessException("User must have REALTOR role to register as an agent");
    }

    // Get organization and verify it's approved
    Organization organization =
        organizationRepository
            .findById(request.getOrganizationId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Organization", request.getOrganizationId()));

    if (organization.getType() != Organization.OrganizationType.REAL_ESTATE_COMPANY) {
      throw new BusinessException("Organization must be a real estate company");
    }

    if (organization.getStatus() != Organization.OrganizationStatus.APPROVED) {
      throw new BusinessException("Only approved real estate companies can have agents");
    }

    // Check if user is already linked to this organization
    if (agentRepository.existsByOrganizationIdAndUserId(request.getOrganizationId(), userId)) {
      throw new BusinessException("User is already registered as an agent for this organization");
    }

    // If registering for a different user, check if current user is a super agent of the
    // organization (skip if admin)
    UUID currentUserId = UserContext.getCurrentUserId();
    if (!userId.equals(currentUserId) && !UserContext.isAdmin()) {
      // Current user is registering another user - must be a super agent
      if (!agentRepository.isSuperAgentOfOrganization(request.getOrganizationId(), currentUserId)) {
        throw new BusinessException(
            "Only super agents can register other agents for their organization");
      }
    }

    // Create agent (not a super agent unless they created the organization)
    RealEstateAgent agent =
        RealEstateAgent.builder()
            .user(user)
            .organization(organization)
            .status(RealEstateAgent.AgentStatus.ACTIVE)
            .isSuperAgent(false) // Only the organization creator is super agent
            .licenseNumber(request.getLicenseNumber())
            .notes(request.getNotes())
            .build();

    RealEstateAgent saved = agentRepository.save(agent);
    return agentMapper.toResponse(saved);
  }

  @Override
  public AgentResponse createAgentForOrganization(CreateAgentRequest request) {
    // Get current user and verify they are a super agent (skip if admin)
    UUID currentUserId = UserContext.getCurrentUserId();
    RealEstateAgent currentAgent =
        agentRepository
            .findByUserId(currentUserId)
            .orElseThrow(() -> new BusinessException("Current user is not registered as an agent"));

    if (!UserContext.isAdmin() && !currentAgent.getIsSuperAgent()) {
      throw new BusinessException("Only super agents can create new agents for their organization");
    }

    Organization organization = currentAgent.getOrganization();

    if (organization.getStatus() != Organization.OrganizationStatus.APPROVED) {
      throw new BusinessException("Organization must be approved before adding agents");
    }

    // Check if email already exists
    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new BusinessException("Email already registered");
    }

    // Check if phone number already exists (if provided)
    if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
      if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
        throw new BusinessException("Phone number already registered");
      }
    }

    // Create new user with REALTOR role
    Set<User.UserRole> roles = new HashSet<>();
    roles.add(User.UserRole.REALTOR);

    User newUser =
        User.builder()
            .email(request.getEmail().toLowerCase().trim())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
            .status(User.UserStatus.ACTIVE) // Super agent created users are active immediately
            .emailVerified(false)
            .phoneVerified(false)
            .roles(roles)
            .build();

    User savedUser = userRepository.save(newUser);

    // Create agent record
    RealEstateAgent agent =
        RealEstateAgent.builder()
            .user(savedUser)
            .organization(organization)
            .status(RealEstateAgent.AgentStatus.ACTIVE)
            .isSuperAgent(false) // Only the organization creator is super agent
            .licenseNumber(request.getLicenseNumber())
            .notes(request.getNotes())
            .build();

    RealEstateAgent savedAgent = agentRepository.save(agent);
    return agentMapper.toResponse(savedAgent);
  }

  @Override
  @Transactional(readOnly = true)
  public AgentResponse getAgentByUserId(UUID userId) {
    RealEstateAgent agent =
        agentRepository
            .findByUserId(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "RealEstateAgent not found for userId: " + userId));
    return agentMapper.toResponse(agent);
  }

  @Override
  @Transactional(readOnly = true)
  public AgentResponse getAgentById(UUID agentId) {
    RealEstateAgent agent =
        agentRepository
            .findById(agentId)
            .orElseThrow(() -> new ResourceNotFoundException("RealEstateAgent", agentId));
    return agentMapper.toResponse(agent);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AgentResponse> getAgentsByOrganizationId(UUID organizationId) {
    return agentRepository.findByOrganizationId(organizationId).stream()
        .map(agentMapper::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  public AgentResponse updateAgentStatus(UUID agentId, RealEstateAgent.AgentStatus status) {
    RealEstateAgent agent =
        agentRepository
            .findById(agentId)
            .orElseThrow(() -> new ResourceNotFoundException("RealEstateAgent", agentId));
    agent.setStatus(status);
    RealEstateAgent updated = agentRepository.save(agent);
    return agentMapper.toResponse(updated);
  }

  @Override
  public AgentResponse updateAgent(UUID agentId, UpdateAgentRequest request) {
    RealEstateAgent agent =
        agentRepository
            .findById(agentId)
            .orElseThrow(() -> new ResourceNotFoundException("RealEstateAgent", agentId));

    // Check if current user is super agent of the agent's organization (skip if admin)
    if (!UserContext.isAdmin()) {
      UUID currentUserId = UserContext.getCurrentUserId();
      RealEstateAgent currentAgent =
          agentRepository
              .findByUserId(currentUserId)
              .orElseThrow(() -> new BusinessException("User is not a real estate agent"));

      if (!currentAgent.getIsSuperAgent()
          || !currentAgent.getOrganizationId().equals(agent.getOrganizationId())) {
        throw new BusinessException("Only super agents can update agents in their organization");
      }
    }

    // Don't allow updating super agent status
    if (agent.getIsSuperAgent()
        && request.getStatus() != null
        && request.getStatus() != agent.getStatus()) {
      throw new BusinessException("Cannot change status of super agent");
    }

    if (request.getLicenseNumber() != null) {
      agent.setLicenseNumber(request.getLicenseNumber());
    }

    if (request.getNotes() != null) {
      agent.setNotes(request.getNotes());
    }

    if (request.getStatus() != null) {
      agent.setStatus(request.getStatus());
    }

    RealEstateAgent updated = agentRepository.save(agent);
    return agentMapper.toResponse(updated);
  }

  @Override
  @Transactional(readOnly = true)
  public void validateAgentCanManageProperty(UUID agentId, UUID propertyCompanyId) {
    // Skip validation if current user is admin
    if (UserContext.isAdmin()) {
      return;
    }

    RealEstateAgent agent =
        agentRepository
            .findById(agentId)
            .orElseThrow(() -> new ResourceNotFoundException("RealEstateAgent", agentId));

    if (agent.getStatus() != RealEstateAgent.AgentStatus.ACTIVE) {
      throw new BusinessException("Only active agents can manage properties");
    }

    if (!agent.getOrganizationId().equals(propertyCompanyId)) {
      throw new BusinessException("Agent can only manage properties belonging to their company");
    }

    if (agent.getOrganization().getStatus() != Organization.OrganizationStatus.APPROVED) {
      throw new BusinessException("Agent's company must be approved to manage properties");
    }
  }
}
