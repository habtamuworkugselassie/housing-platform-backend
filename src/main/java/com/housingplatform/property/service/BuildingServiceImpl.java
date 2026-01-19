package com.housingplatform.property.service;

import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.property.domain.Building;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.BuildingRequest;
import com.housingplatform.property.dto.BuildingResponse;
import com.housingplatform.property.dto.PropertyResponse;
import com.housingplatform.property.repository.BuildingRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.property.service.PropertyMapper;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BuildingServiceImpl implements BuildingService {
    
    private final BuildingRepository buildingRepository;
    private final PropertyRepository propertyRepository;
    private final OrganizationRepository organizationRepository;
    private final BuildingMapper buildingMapper;
    private final PropertyMapper propertyMapper;
    
    @Override
    public BuildingResponse createBuilding(UUID companyId, BuildingRequest request) {
        // Verify organization exists and is a real estate company
        organizationRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", companyId));
        
        Building building = buildingMapper.toEntity(request);
        building.setRealEstateCompanyId(companyId);
        
        if (request.getStatus() == null) {
            building.setStatus(Building.BuildingStatus.PLANNED);
        }
        
        Building saved = buildingRepository.save(building);
        return enrichBuildingResponse(saved);
    }
    
    @Override
    @Transactional(readOnly = true)
    public BuildingResponse getBuildingById(UUID id) {
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Building", id));
        return enrichBuildingResponse(building);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BuildingResponse> getBuildingsByCompanyId(UUID companyId) {
        return buildingRepository.findByRealEstateCompanyId(companyId)
                .stream()
                .map(this::enrichBuildingResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BuildingResponse> getBuildingsByAgentId(UUID agentId) {
        return buildingRepository.findByAgentId(agentId)
                .stream()
                .map(this::enrichBuildingResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public BuildingResponse updateBuilding(UUID companyId, UUID buildingId, BuildingRequest request) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", buildingId));
        
        if (!building.getRealEstateCompanyId().equals(companyId)) {
            throw new BusinessException("Building does not belong to the specified company");
        }
        
        buildingMapper.updateEntity(building, request);
        Building updated = buildingRepository.save(building);
        return enrichBuildingResponse(updated);
    }
    
    @Override
    public void deleteBuilding(UUID companyId, UUID buildingId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", buildingId));
        
        if (!building.getRealEstateCompanyId().equals(companyId)) {
            throw new BusinessException("Building does not belong to the specified company");
        }
        
        // Check if building has units
        long unitCount = propertyRepository.findAll().stream()
                .filter(p -> p.getBuilding() != null && p.getBuilding().getId().equals(buildingId))
                .count();
        
        if (unitCount > 0) {
            throw new BusinessException("Cannot delete building with existing units. Please remove all units first.");
        }
        
        buildingRepository.delete(building);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BuildingResponse> getAllBuildings(String city, String buildingType) {
        List<Building> buildings;
        
        if (city != null && buildingType != null) {
            buildings = buildingRepository.findAll().stream()
                    .filter(b -> b.getCity().equalsIgnoreCase(city) && 
                            b.getBuildingType().name().equalsIgnoreCase(buildingType))
                    .collect(Collectors.toList());
        } else if (city != null) {
            buildings = buildingRepository.findByCity(city);
        } else if (buildingType != null) {
            buildings = buildingRepository.findByBuildingType(Building.BuildingType.valueOf(buildingType.toUpperCase()));
        } else {
            buildings = buildingRepository.findAll();
        }
        
        return buildings.stream()
                .map(this::enrichBuildingResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BuildingResponse> searchBuildings(String companyName, String city, String state, String country, String name, Integer limit) {
        List<Building> buildings = buildingRepository.findAll();
        
        // Filter by name (partial match)
        if (name != null && !name.trim().isEmpty()) {
            String nameLower = name.toLowerCase();
            buildings = buildings.stream()
                    .filter(b -> b.getName().toLowerCase().contains(nameLower))
                    .collect(Collectors.toList());
        }
        
        // Filter by city
        if (city != null && !city.trim().isEmpty()) {
            String cityLower = city.toLowerCase();
            buildings = buildings.stream()
                    .filter(b -> b.getCity() != null && b.getCity().toLowerCase().contains(cityLower))
                    .collect(Collectors.toList());
        }
        
        // Filter by state
        if (state != null && !state.trim().isEmpty()) {
            String stateLower = state.toLowerCase();
            buildings = buildings.stream()
                    .filter(b -> b.getState() != null && b.getState().toLowerCase().contains(stateLower))
                    .collect(Collectors.toList());
        }
        
        // Filter by country
        if (country != null && !country.trim().isEmpty()) {
            String countryLower = country.toLowerCase();
            buildings = buildings.stream()
                    .filter(b -> b.getCountry() != null && b.getCountry().toLowerCase().contains(countryLower))
                    .collect(Collectors.toList());
        }
        
        // Filter by company name if provided
        if (companyName != null && !companyName.trim().isEmpty()) {
            String companyNameLower = companyName.toLowerCase();
            List<UUID> matchingCompanyIds = organizationRepository.findAll().stream()
                    .filter(org -> org.getName().toLowerCase().contains(companyNameLower))
                    .map(com.housingplatform.identity.domain.Organization::getId)
                    .collect(Collectors.toList());
            
            buildings = buildings.stream()
                    .filter(b -> matchingCompanyIds.contains(b.getRealEstateCompanyId()))
                    .collect(Collectors.toList());
        }
        
        // Limit results
        if (limit != null && limit > 0) {
            buildings = buildings.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        
        return buildings.stream()
                .map(this::enrichBuildingResponse)
                .collect(Collectors.toList());
    }
    
    private BuildingResponse enrichBuildingResponse(Building building) {
        BuildingResponse response = buildingMapper.toResponse(building);
        
        // Get organization name
        organizationRepository.findById(building.getRealEstateCompanyId())
                .ifPresent(org -> response.setRealEstateCompanyName(org.getName()));
        
        // Get all units for this building
        List<Property> units = propertyRepository.findAll().stream()
                .filter(p -> p.getBuilding() != null && p.getBuilding().getId().equals(building.getId()))
                .collect(Collectors.toList());
        
        long availableUnits = units.stream()
                .filter(p -> p.getStatus() == Property.PropertyStatus.AVAILABLE)
                .count();
        
        long occupiedUnits = units.stream()
                .filter(p -> p.getStatus() == Property.PropertyStatus.SOLD || 
                           p.getStatus() == Property.PropertyStatus.RESERVED)
                .count();
        
        response.setAvailableUnits((int) availableUnits);
        response.setOccupiedUnits((int) occupiedUnits);
        
        // Map units to PropertyResponse
        List<PropertyResponse> unitResponses = units.stream()
                .map(propertyMapper::toResponseWithImages)
                .collect(Collectors.toList());
        response.setUnits(unitResponses);
        
        return response;
    }
}
