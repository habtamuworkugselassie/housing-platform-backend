package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.MaterialUsage;
import com.housingplatform.construction.dto.MaterialUsageRequest;
import com.housingplatform.construction.dto.MaterialUsageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MaterialUsageMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "totalCost", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  MaterialUsage toEntity(MaterialUsageRequest request);

  @Mapping(target = "projectName", ignore = true)
  @Mapping(target = "phaseName", ignore = true)
  @Mapping(target = "materialName", ignore = true)
  @Mapping(target = "orderNumber", ignore = true)
  @Mapping(target = "usedByName", ignore = true)
  MaterialUsageResponse toResponse(MaterialUsage usage);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "totalCost", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  void updateEntity(@MappingTarget MaterialUsage usage, MaterialUsageRequest request);
}
