package com.housingplatform.property.service;

import com.housingplatform.property.dto.BuildingRequest;
import com.housingplatform.property.dto.BuildingResponse;
import java.util.List;
import java.util.UUID;

public interface BuildingService {
  BuildingResponse createBuilding(UUID companyId, BuildingRequest request);

  BuildingResponse getBuildingById(UUID id);

  List<BuildingResponse> getBuildingsByCompanyId(UUID companyId);

  List<BuildingResponse> getBuildingsByAgentId(UUID agentId);

  BuildingResponse updateBuilding(UUID companyId, UUID buildingId, BuildingRequest request);

  void deleteBuilding(UUID companyId, UUID buildingId);

  List<BuildingResponse> getAllBuildings(String city, String buildingType);

  List<BuildingResponse> searchBuildings(
      String companyName, String city, String state, String country, String name, Integer limit);
}
