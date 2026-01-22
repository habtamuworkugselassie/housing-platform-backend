package com.housingplatform.property.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.SponsorshipApplication;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.SponsorshipApplicationRepository;
import com.housingplatform.identity.service.RealEstateAgentService;
import com.housingplatform.property.domain.Building;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.PropertyRequest;
import com.housingplatform.property.dto.PropertyResponse;
import com.housingplatform.property.repository.BuildingRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyServiceImpl implements PropertyService {
    
    private final PropertyRepository propertyRepository;
    private final BuildingRepository buildingRepository;
    private final PropertyMapper propertyMapper;
    private final RealEstateAgentService agentService;
    private final OrganizationRepository organizationRepository;
    private final SponsorshipApplicationRepository sponsorshipApplicationRepository;
    
    @Override
    public PropertyResponse createProperty(PropertyRequest request, UUID agentId) {
        // Validate agent can manage properties for this company
        if (agentId != null) {
            agentService.validateAgentCanManageProperty(agentId, request.getRealEstateCompanyId());
        }
        
        Property property = propertyMapper.toEntity(request);
        
        // Validate that at least one price is provided
        if (property.getPriceETB() == null && property.getPriceUSD() == null) {
            throw new BusinessException("At least one price (ETB or USD) must be provided");
        }
        
        property.setAgentId(agentId);
        property.setStatus(Property.PropertyStatus.AVAILABLE);
        property.setVerificationStatus(Property.VerificationStatus.PENDING);
        
        // Set building relationship if provided
        if (request.getBuildingId() != null) {
            Building building = buildingRepository.findById(request.getBuildingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Building", request.getBuildingId()));
            property.setBuilding(building);
            property.setUnitNumber(request.getUnitNumber());
        }
        
        Property saved = propertyRepository.save(property);
        return propertyMapper.toResponseWithImages(saved);
    }
    
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "properties", key = "#id")
    public PropertyResponse getPropertyById(UUID id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));
        return propertyMapper.toResponseWithImages(property);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<PropertyResponse> getAllProperties(String status, String city, Pageable pageable, boolean publicOnly) {
        Specification<Property> spec = Specification.where(null);
        
        // For public access, only show AVAILABLE properties (ignore status parameter for security)
        // For authenticated users, respect the status parameter
        if (publicOnly) {
            spec = spec.and((root, query, cb) -> 
                cb.equal(root.get("status"), Property.PropertyStatus.AVAILABLE));
        } else if (status != null && !status.trim().isEmpty()) {
            try {
                Property.PropertyStatus statusEnum = Property.PropertyStatus.valueOf(status.toUpperCase());
                spec = spec.and((root, query, cb) -> 
                    cb.equal(root.get("status"), statusEnum));
            } catch (IllegalArgumentException e) {
                // Invalid status value, ignore it
            }
        }
        
        if (city != null) {
            spec = spec.and((root, query, cb) -> 
                cb.equal(cb.lower(root.get("city")), city.toLowerCase()));
        }
        
        // Get all properties matching the criteria
        List<Property> allProperties = propertyRepository.findAll(spec);
        
        // Fetch all organizations for company name (optimized: single query instead of N+1)
        Set<UUID> organizationIds = allProperties.stream()
            .map(Property::getRealEstateCompanyId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        
        Map<UUID, Organization> organizationsMap = organizationIds.isEmpty() 
            ? java.util.Collections.emptyMap()
            : organizationRepository.findAllById(organizationIds).stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));
        
        // Fetch all active sponsorship applications
        List<SponsorshipApplication> activeApplications = sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
        Map<UUID, SponsorshipApplication> applicationMap = activeApplications.stream()
            .collect(Collectors.toMap(
                app -> app.getOrganization().getId(),
                Function.identity(),
                (existing, replacement) -> {
                    // If multiple active applications exist, prefer PREMIER over BASIC
                    if (replacement.getSponsorship().getType() == com.housingplatform.identity.domain.Sponsorship.SponsorshipType.PREMIER) {
                        return replacement;
                    }
                    return existing;
                }
            ));
        
        // Sort by sponsorship priority: Premier > Basic > None
        // Then by creation date (newest first)
        List<Property> sortedProperties = allProperties.stream()
            .sorted(Comparator
                .comparing((Property p) -> {
                    SponsorshipApplication application = applicationMap.get(p.getRealEstateCompanyId());
                    if (application != null && application.isActive()) {
                        return application.getSponsorship().getType() == com.housingplatform.identity.domain.Sponsorship.SponsorshipType.PREMIER ? 0 : 1;
                    }
                    return 2; // Non-sponsored
                })
                .thenComparing(Property::getCreatedAt, Comparator.reverseOrder())
            )
            .collect(Collectors.toList());
        
        // Apply pagination manually
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), sortedProperties.size());
        List<Property> pagedProperties = sortedProperties.subList(start, end);
        
        // Map to response and enrich with sponsorship info
        List<PropertyResponse> responses = pagedProperties.stream()
            .map(property -> {
                PropertyResponse response = propertyMapper.toResponseWithImages(property);
                enrichWithSponsorshipInfo(response, property, organizationsMap, applicationMap);
                return response;
            })
            .collect(Collectors.toList());
        
        return new org.springframework.data.domain.PageImpl<>(
            responses,
            pageable,
            sortedProperties.size()
        );
    }
    
    private void enrichWithSponsorshipInfo(PropertyResponse response, Property property, 
                                          Map<UUID, Organization> organizationsMap, 
                                          Map<UUID, SponsorshipApplication> applicationMap) {
        if (property.getRealEstateCompanyId() != null) {
            Organization org = organizationsMap.get(property.getRealEstateCompanyId());
            if (org != null) {
                response.setRealEstateCompanyName(org.getName());
            }
            
            SponsorshipApplication application = applicationMap.get(property.getRealEstateCompanyId());
            if (application != null && application.isActive()) {
                response.setIsSponsored(true);
                response.setSponsorshipType(application.getSponsorship().getType().name());
            } else {
                response.setIsSponsored(false);
            }
        }
    }
    
    @Override
    @CacheEvict(value = "properties", key = "#id")
    public PropertyResponse updateProperty(UUID id, PropertyRequest request, UUID agentId) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));
        
        // Validate agent can manage this property
        if (agentId != null) {
            com.housingplatform.identity.dto.AgentResponse agent = agentService.getAgentById(agentId);
            
            // Check if agent is super agent of the organization that owns the property
            boolean isSuperAgent = Boolean.TRUE.equals(agent.getIsSuperAgent()) && 
                    agent.getOrganizationId().equals(property.getRealEstateCompanyId());
            
            // Allow if: agent owns the property OR agent is super agent of the organization
            if (!property.getAgentId().equals(agentId) && !isSuperAgent) {
                throw new BusinessException("Agent can only update properties they manage or if they are super agent of the organization");
            }
            
            // Validate the property belongs to the agent's organization
            if (!agent.getOrganizationId().equals(property.getRealEstateCompanyId())) {
                throw new BusinessException("Property does not belong to your organization");
            }
            
            // Only validate agent can manage property if not super agent (super agents can manage all org properties)
            if (!isSuperAgent) {
                agentService.validateAgentCanManageProperty(agentId, request.getRealEstateCompanyId());
            }
        }
        
        propertyMapper.updateEntity(property, request);
        Property updated = propertyRepository.save(property);
        return propertyMapper.toResponseWithImages(updated);
    }
    
    @Override
    @CacheEvict(value = "properties", key = "#id")
    public void deleteProperty(UUID id, UUID agentId) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));
        
        // Validate agent can manage this property
        if (agentId != null) {
            com.housingplatform.identity.dto.AgentResponse agent = agentService.getAgentById(agentId);
            
            // Check if agent is super agent of the organization that owns the property
            boolean isSuperAgent = Boolean.TRUE.equals(agent.getIsSuperAgent()) && 
                    agent.getOrganizationId().equals(property.getRealEstateCompanyId());
            
            // Allow if: agent owns the property OR agent is super agent of the organization
            if (!property.getAgentId().equals(agentId) && !isSuperAgent) {
                throw new BusinessException("Agent can only delete properties they manage or if they are super agent of the organization");
            }
            
            // Validate the property belongs to the agent's organization
            if (!agent.getOrganizationId().equals(property.getRealEstateCompanyId())) {
                throw new BusinessException("Property does not belong to your organization");
            }
        }
        
        propertyRepository.delete(property);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<PropertyResponse> getPropertiesByCompanyId(UUID companyId) {
        return propertyRepository.findByRealEstateCompanyId(companyId)
                .stream()
                .map(propertyMapper::toResponseWithImages)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<PropertyResponse> getPropertiesByAgentId(UUID agentId) {
        return propertyRepository.findByAgentId(agentId)
                .stream()
                .map(propertyMapper::toResponseWithImages)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<PropertyResponse> searchProperties(String companyName, String city, String state, String country, String title, Integer limit) {
        Specification<Property> spec = Specification.where(null);
        
        // Search by title (partial match)
        if (title != null && !title.trim().isEmpty()) {
            String titleLower = title.toLowerCase();
            spec = spec.and((root, query, cb) -> 
                cb.like(cb.lower(root.get("title")), "%" + titleLower + "%"));
        }
        
        // Search by city
        if (city != null && !city.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> 
                cb.like(cb.lower(root.get("city")), "%" + city.toLowerCase() + "%"));
        }
        
        // Search by state
        if (state != null && !state.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> 
                cb.like(cb.lower(root.get("state")), "%" + state.toLowerCase() + "%"));
        }
        
        // Search by country
        if (country != null && !country.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> 
                cb.like(cb.lower(root.get("country")), "%" + country.toLowerCase() + "%"));
        }
        
        // Get all matching properties
        List<Property> properties = propertyRepository.findAll(spec);
        
        // Filter by company name if provided
        if (companyName != null && !companyName.trim().isEmpty()) {
            String companyNameLower = companyName.toLowerCase();
            List<UUID> matchingCompanyIds = organizationRepository.findAll().stream()
                    .filter(org -> org.getName().toLowerCase().contains(companyNameLower))
                    .map(Organization::getId)
                    .collect(Collectors.toList());
            
            properties = properties.stream()
                    .filter(p -> p.getRealEstateCompanyId() != null && 
                            matchingCompanyIds.contains(p.getRealEstateCompanyId()))
                    .collect(Collectors.toList());
        }
        
        // Limit results
        if (limit != null && limit > 0) {
            properties = properties.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        
        // Fetch organizations for company names (optimized: single query instead of N+1)
        Set<UUID> organizationIds = properties.stream()
                .map(Property::getRealEstateCompanyId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        
        Map<UUID, Organization> organizationsMap = organizationIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : organizationRepository.findAllById(organizationIds).stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));
        
        // Map to response and enrich with company names
        return properties.stream()
                .map(property -> {
                    PropertyResponse response = propertyMapper.toResponseWithImages(property);
                    Organization org = organizationsMap.get(property.getRealEstateCompanyId());
                    if (org != null) {
                        response.setRealEstateCompanyName(org.getName());
                    }
                    return response;
                })
                .collect(Collectors.toList());
    }
}
