package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationServiceImpl implements OrganizationService {
    
    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;
    private final UserRepository userRepository;
    private final RealEstateAgentRepository realEstateAgentRepository;
    
    @Override
    public OrganizationResponse createOrganization(OrganizationRequest request) {
        // Check if registration number already exists
        if (request.getRegistrationNumber() != null && 
            organizationRepository.findByRegistrationNumber(request.getRegistrationNumber()).isPresent()) {
            throw new BusinessException("Organization with registration number already exists");
        }
        
        Organization organization = organizationMapper.toEntity(request);
        organization.setStatus(Organization.OrganizationStatus.PENDING_APPROVAL);
        Organization saved = organizationRepository.save(organization);
        
        // If a REALTOR is creating a REAL_ESTATE_COMPANY, automatically create a RealEstateAgent
        // and mark them as the super agent (owner)
        if (saved.getType() == Organization.OrganizationType.REAL_ESTATE_COMPANY) {
            try {
                UUID currentUserId = UserContext.getCurrentUserId();
                User currentUser = userRepository.findById(currentUserId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));
                
                // Check if user has REALTOR role
                if (currentUser.getRoles().contains(User.UserRole.REALTOR)) {
                    // Check if user is already an agent
                    if (!realEstateAgentRepository.existsByUserId(currentUserId)) {
                        // Create RealEstateAgent and mark as super agent
                        RealEstateAgent superAgent = RealEstateAgent.builder()
                                .user(currentUser)
                                .organization(saved)
                                .status(RealEstateAgent.AgentStatus.ACTIVE)
                                .isSuperAgent(true)
                                .build();
                        realEstateAgentRepository.save(superAgent);
                    } else {
                        throw new BusinessException("User is already registered as a real estate agent for another organization");
                    }
                }
            } catch (IllegalStateException e) {
                // User context not available (shouldn't happen in authenticated endpoint, but handle gracefully)
                // This means the organization was created but no agent was linked
            }
        }
        
        return organizationMapper.toResponse(saved);
    }
    
    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(UUID id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        return organizationMapper.toResponse(organization);
    }
    
    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getMyOrganization() {
        UUID currentUserId = UserContext.getCurrentUserId();
        RealEstateAgent agent = realEstateAgentRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("RealEstateAgent not found for current user"));
        
        if (!agent.getIsSuperAgent()) {
            throw new BusinessException("Only super agents can access their organization details");
        }
        
        return organizationMapper.toResponse(agent.getOrganization());
    }
    
    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getMyBank() {
        UUID currentUserId = UserContext.getCurrentUserId();
        
        // Find bank where user is primary contact
        List<Organization> banks = organizationRepository.findByType(Organization.OrganizationType.BANK);
        Organization bank = banks.stream()
                .filter(b -> b.getPrimaryContact() != null && b.getPrimaryContact().getId().equals(currentUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Bank not found for current user. User must be primary contact of a bank organization."));
        
        return organizationMapper.toResponse(bank);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponse> getAllOrganizations(String type, String status) {
        List<Organization> organizations;
        
        if (type != null && status != null) {
            organizations = organizationRepository.findByTypeAndStatus(
                    Organization.OrganizationType.valueOf(type.toUpperCase()),
                    Organization.OrganizationStatus.valueOf(status.toUpperCase())
            );
        } else if (type != null) {
            organizations = organizationRepository.findByType(
                    Organization.OrganizationType.valueOf(type.toUpperCase())
            );
        } else if (status != null) {
            organizations = organizationRepository.findByStatus(
                    Organization.OrganizationStatus.valueOf(status.toUpperCase())
            );
        } else {
            organizations = organizationRepository.findAll();
        }
        
        return organizations.stream()
                .map(organizationMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public OrganizationResponse updateOrganization(UUID id, OrganizationRequest request) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        
        // Check if current user is super agent of this organization
        try {
            UUID currentUserId = UserContext.getCurrentUserId();
            RealEstateAgent agent = realEstateAgentRepository.findByUserId(currentUserId)
                    .orElseThrow(() -> new BusinessException("User is not a real estate agent"));
            
            if (!agent.getIsSuperAgent() || !agent.getOrganizationId().equals(id)) {
                throw new BusinessException("Only super agents can update their own organization");
            }
        } catch (IllegalStateException e) {
            // User context not available - allow if admin (will be checked by security)
        }
        
        organizationMapper.updateEntity(organization, request);
        Organization updated = organizationRepository.save(organization);
        return organizationMapper.toResponse(updated);
    }
    
    @Override
    public OrganizationResponse approveOrganization(UUID id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        
        organization.setStatus(Organization.OrganizationStatus.APPROVED);
        Organization updated = organizationRepository.save(organization);
        return organizationMapper.toResponse(updated);
    }
    
    @Override
    public OrganizationResponse rejectOrganization(UUID id, String reason) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        
        organization.setStatus(Organization.OrganizationStatus.REJECTED);
        Organization updated = organizationRepository.save(organization);
        return organizationMapper.toResponse(updated);
    }
    
    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getMySupplier() {
        UUID currentUserId = UserContext.getCurrentUserId();
        
        // Find supplier where user is primary contact
        List<Organization> suppliers = organizationRepository.findByType(Organization.OrganizationType.SUPPLIER);
        Organization supplier = suppliers.stream()
                .filter(s -> s.getPrimaryContact() != null && s.getPrimaryContact().getId().equals(currentUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found for current user. User must be primary contact of a supplier organization."));
        
        return organizationMapper.toResponse(supplier);
    }
}
