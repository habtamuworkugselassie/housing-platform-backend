package com.housingplatform.property.repository;

import com.housingplatform.property.domain.Building;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingRepository extends JpaRepository<Building, UUID> {
  List<Building> findByRealEstateCompanyId(UUID companyId);

  List<Building> findByAgentId(UUID agentId);

  List<Building> findByStatus(Building.BuildingStatus status);

  List<Building> findByCity(String city);

  List<Building> findByBuildingType(Building.BuildingType buildingType);
}
