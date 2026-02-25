package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.dto.ConstructionProjectRequest;
import com.housingplatform.construction.dto.ConstructionProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ConstructionProjectMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "totalCost", ignore = true)
  @Mapping(target = "actualEndDate", ignore = true)
  @Mapping(target = "phases", ignore = true)
  @Mapping(target = "orders", ignore = true)
  @Mapping(target = "billsOfQuantities", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  ConstructionProject toEntity(ConstructionProjectRequest request);

  @Mapping(target = "realEstateCompanyName", ignore = true)
  @Mapping(target = "projectManagerName", ignore = true)
  @Mapping(target = "phases", ignore = true)
  @Mapping(target = "totalPhases", ignore = true)
  @Mapping(target = "completedPhases", ignore = true)
  @Mapping(target = "overallCompletionPercentage", ignore = true)
  ConstructionProjectResponse toResponse(ConstructionProject project);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "totalCost", ignore = true)
  @Mapping(target = "actualEndDate", ignore = true)
  @Mapping(target = "phases", ignore = true)
  @Mapping(target = "orders", ignore = true)
  @Mapping(target = "billsOfQuantities", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  void updateEntity(@MappingTarget ConstructionProject project, ConstructionProjectRequest request);
}
