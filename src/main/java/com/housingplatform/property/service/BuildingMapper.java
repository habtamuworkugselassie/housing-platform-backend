package com.housingplatform.property.service;

import com.housingplatform.property.domain.Building;
import com.housingplatform.property.dto.BuildingRequest;
import com.housingplatform.property.dto.BuildingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface BuildingMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "realEstateCompanyId", ignore = true)
    @Mapping(target = "units", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Building toEntity(BuildingRequest request);
    
    @Mapping(target = "realEstateCompanyName", ignore = true)
    @Mapping(target = "availableUnits", ignore = true)
    @Mapping(target = "occupiedUnits", ignore = true)
    @Mapping(target = "units", ignore = true)
    BuildingResponse toResponse(Building building);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "realEstateCompanyId", ignore = true)
    @Mapping(target = "units", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(@MappingTarget Building building, BuildingRequest request);
}
