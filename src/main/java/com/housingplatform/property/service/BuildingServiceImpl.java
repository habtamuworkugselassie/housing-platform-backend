package com.housingplatform.property.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.SponsorshipApplication;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.SponsorshipApplicationRepository;
import com.housingplatform.property.domain.Building;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.BuildingRequest;
import com.housingplatform.property.dto.BuildingResponse;
import com.housingplatform.property.dto.PropertyResponse;
import com.housingplatform.property.repository.BuildingRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BuildingServiceImpl implements BuildingService {

  private final BuildingRepository buildingRepository;
  private final PropertyRepository propertyRepository;
  private final OrganizationRepository organizationRepository;
  private final SponsorshipApplicationRepository sponsorshipApplicationRepository;
  private final BuildingMapper buildingMapper;
  private final PropertyMapper propertyMapper;

  @Override
  public BuildingResponse createBuilding(UUID companyId, BuildingRequest request) {
    // Verify organization exists and is a real estate company
    organizationRepository
        .findById(companyId)
        .orElseThrow(() -> new ResourceNotFoundException("Organization", companyId));

    Building building = buildingMapper.toEntity(request);
    building.setRealEstateCompanyId(companyId);

    if (request.getStatus() == null) {
      building.setStatus(Building.BuildingStatus.PLANNED);
    }

    Building saved = buildingRepository.save(building);

    // Fetch active sponsorship applications for enrichment
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIUM) {
                        return replacement;
                      }
                      return existing;
                    }));

    return enrichBuildingResponse(saved, applicationMap);
  }

  @Override
  @Transactional(readOnly = true)
  public BuildingResponse getBuildingById(UUID id) {
    Building building =
        buildingRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Building", id));

    // Fetch active sponsorship applications for enrichment
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIUM) {
                        return replacement;
                      }
                      return existing;
                    }));

    return enrichBuildingResponse(building, applicationMap);
  }

  @Override
  @Transactional(readOnly = true)
  public List<BuildingResponse> getBuildingsByCompanyId(UUID companyId) {
    // Fetch active sponsorship applications for enrichment
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIUM) {
                        return replacement;
                      }
                      return existing;
                    }));

    return buildingRepository.findByRealEstateCompanyId(companyId).stream()
        .map(building -> enrichBuildingResponse(building, applicationMap))
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<BuildingResponse> getBuildingsByAgentId(UUID agentId) {
    // Fetch active sponsorship applications for enrichment
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIUM) {
                        return replacement;
                      }
                      return existing;
                    }));

    return buildingRepository.findByAgentId(agentId).stream()
        .map(building -> enrichBuildingResponse(building, applicationMap))
        .collect(Collectors.toList());
  }

  @Override
  public BuildingResponse updateBuilding(UUID companyId, UUID buildingId, BuildingRequest request) {
    Building building =
        buildingRepository
            .findById(buildingId)
            .orElseThrow(() -> new ResourceNotFoundException("Building", buildingId));

    // Skip ownership check if admin
    if (!com.housingplatform.shared.security.UserContext.isAdmin()
        && !building.getRealEstateCompanyId().equals(companyId)) {
      throw new BusinessException("Building does not belong to the specified company");
    }

    buildingMapper.updateEntity(building, request);
    Building updated = buildingRepository.save(building);

    // Fetch active sponsorship applications for enrichment
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIUM) {
                        return replacement;
                      }
                      return existing;
                    }));

    return enrichBuildingResponse(updated, applicationMap);
  }

  @Override
  public void deleteBuilding(UUID companyId, UUID buildingId) {
    Building building =
        buildingRepository
            .findById(buildingId)
            .orElseThrow(() -> new ResourceNotFoundException("Building", buildingId));

    // Skip ownership check if admin
    if (!com.housingplatform.shared.security.UserContext.isAdmin()
        && !building.getRealEstateCompanyId().equals(companyId)) {
      throw new BusinessException("Building does not belong to the specified company");
    }

    // Check if building has units
    long unitCount =
        propertyRepository.findAll().stream()
            .filter(p -> p.getBuilding() != null && p.getBuilding().getId().equals(buildingId))
            .count();

    if (unitCount > 0) {
      throw new BusinessException(
          "Cannot delete building with existing units. Please remove all units first.");
    }

    buildingRepository.delete(building);
  }

  @Override
  @Transactional(readOnly = true)
  public List<BuildingResponse> getAllBuildings(String city, String buildingType) {
    List<Building> buildings;

    if (city != null && buildingType != null) {
      buildings =
          buildingRepository.findAll().stream()
              .filter(
                  b ->
                      b.getCity().equalsIgnoreCase(city)
                          && b.getBuildingType().name().equalsIgnoreCase(buildingType))
              .collect(Collectors.toList());
    } else if (city != null) {
      buildings = buildingRepository.findByCity(city);
    } else if (buildingType != null) {
      buildings =
          buildingRepository.findByBuildingType(
              Building.BuildingType.valueOf(buildingType.toUpperCase()));
    } else {
      buildings = buildingRepository.findAll();
    }

    // Exclude buildings belonging to suspended organizations (public list)
    if (!buildings.isEmpty()) {
      java.util.Set<UUID> companyIds =
          buildings.stream()
              .map(Building::getRealEstateCompanyId)
              .filter(java.util.Objects::nonNull)
              .collect(Collectors.toSet());
      Map<UUID, Organization> orgsMap =
          organizationRepository.findAllById(companyIds).stream()
              .collect(Collectors.toMap(Organization::getId, Function.identity()));
      buildings =
          buildings.stream()
              .filter(
                  b -> {
                    Organization org = orgsMap.get(b.getRealEstateCompanyId());
                    return org == null
                        || org.getStatus() != Organization.OrganizationStatus.SUSPENDED;
                  })
              .collect(Collectors.toList());
    }

    // Fetch active sponsorship applications for enrichment
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      // If multiple active applications exist, prefer PREMIUM over GOLD
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIUM) {
                        return replacement;
                      }
                      return existing;
                    }));

    return buildings.stream()
        .map(building -> enrichBuildingResponse(building, applicationMap))
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<BuildingResponse> searchBuildings(
      String companyName, String city, String state, String country, String name, Integer limit) {
    List<Building> buildings = buildingRepository.findAll();

    // Filter by name (partial match)
    if (name != null && !name.trim().isEmpty()) {
      String nameLower = name.toLowerCase();
      buildings =
          buildings.stream()
              .filter(b -> b.getName().toLowerCase().contains(nameLower))
              .collect(Collectors.toList());
    }

    // Filter by city
    if (city != null && !city.trim().isEmpty()) {
      String cityLower = city.toLowerCase();
      buildings =
          buildings.stream()
              .filter(b -> b.getCity() != null && b.getCity().toLowerCase().contains(cityLower))
              .collect(Collectors.toList());
    }

    // Filter by state
    if (state != null && !state.trim().isEmpty()) {
      String stateLower = state.toLowerCase();
      buildings =
          buildings.stream()
              .filter(b -> b.getState() != null && b.getState().toLowerCase().contains(stateLower))
              .collect(Collectors.toList());
    }

    // Filter by country
    if (country != null && !country.trim().isEmpty()) {
      String countryLower = country.toLowerCase();
      buildings =
          buildings.stream()
              .filter(
                  b ->
                      b.getCountry() != null && b.getCountry().toLowerCase().contains(countryLower))
              .collect(Collectors.toList());
    }

    // Filter by company name if provided
    if (companyName != null && !companyName.trim().isEmpty()) {
      String companyNameLower = companyName.toLowerCase();
      List<UUID> matchingCompanyIds =
          organizationRepository.findAll().stream()
              .filter(org -> org.getName().toLowerCase().contains(companyNameLower))
              .map(com.housingplatform.identity.domain.Organization::getId)
              .collect(Collectors.toList());

      buildings =
          buildings.stream()
              .filter(b -> matchingCompanyIds.contains(b.getRealEstateCompanyId()))
              .collect(Collectors.toList());
    }

    // Limit results
    if (limit != null && limit > 0) {
      buildings = buildings.stream().limit(limit).collect(Collectors.toList());
    }

    // Exclude buildings belonging to suspended organizations (public search)
    if (!buildings.isEmpty()) {
      java.util.Set<UUID> companyIds =
          buildings.stream()
              .map(Building::getRealEstateCompanyId)
              .filter(java.util.Objects::nonNull)
              .collect(Collectors.toSet());
      Map<UUID, Organization> orgsMap =
          organizationRepository.findAllById(companyIds).stream()
              .collect(Collectors.toMap(Organization::getId, Function.identity()));
      buildings =
          buildings.stream()
              .filter(
                  b -> {
                    Organization org = orgsMap.get(b.getRealEstateCompanyId());
                    return org == null
                        || org.getStatus() != Organization.OrganizationStatus.SUSPENDED;
                  })
              .collect(Collectors.toList());
    }

    // Fetch active sponsorship applications for enrichment
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      // If multiple active applications exist, prefer PREMIUM over GOLD
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIUM) {
                        return replacement;
                      }
                      return existing;
                    }));

    return buildings.stream()
        .map(building -> enrichBuildingResponse(building, applicationMap))
        .collect(Collectors.toList());
  }

  private BuildingResponse enrichBuildingResponse(Building building) {
    return enrichBuildingResponse(building, java.util.Collections.emptyMap());
  }

  private BuildingResponse enrichBuildingResponse(
      Building building, Map<UUID, SponsorshipApplication> applicationMap) {
    BuildingResponse response = buildingMapper.toResponse(building);

    // Get organization name
    organizationRepository
        .findById(building.getRealEstateCompanyId())
        .ifPresent(org -> response.setRealEstateCompanyName(org.getName()));

    // Enrich with sponsorship info
    if (building.getRealEstateCompanyId() != null && !applicationMap.isEmpty()) {
      SponsorshipApplication application = applicationMap.get(building.getRealEstateCompanyId());
      if (application != null && application.isActive()) {
        response.setIsSponsored(true);
        response.setSponsorshipType(application.getSponsorship().getType().name());
      } else {
        response.setIsSponsored(false);
      }
    } else {
      response.setIsSponsored(false);
    }

    // Get all units for this building
    List<Property> units =
        propertyRepository.findAll().stream()
            .filter(
                p -> p.getBuilding() != null && p.getBuilding().getId().equals(building.getId()))
            .collect(Collectors.toList());

    long availableUnits =
        units.stream().filter(p -> p.getStatus() == Property.PropertyStatus.AVAILABLE).count();

    long occupiedUnits =
        units.stream()
            .filter(
                p ->
                    p.getStatus() == Property.PropertyStatus.SOLD
                        || p.getStatus() == Property.PropertyStatus.RESERVED)
            .count();

    response.setAvailableUnits((int) availableUnits);
    response.setOccupiedUnits((int) occupiedUnits);

    // Map units to PropertyResponse
    List<PropertyResponse> unitResponses =
        units.stream().map(propertyMapper::toResponseWithImages).collect(Collectors.toList());
    response.setUnits(unitResponses);

    return response;
  }
}
