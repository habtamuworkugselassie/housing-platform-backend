package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.ConstructionPhase;
import com.housingplatform.construction.dto.ConstructionPhaseRequest;
import com.housingplatform.construction.dto.ConstructionPhaseResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ConstructionPhaseMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "completionPercentage", ignore = true)
    @Mapping(target = "actualCost", ignore = true)
    @Mapping(target = "actualEndDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ConstructionPhase toEntity(ConstructionPhaseRequest request);
    
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    ConstructionPhaseResponse toResponse(ConstructionPhase phase);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "completionPercentage", ignore = true)
    @Mapping(target = "actualCost", ignore = true)
    @Mapping(target = "actualEndDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget ConstructionPhase phase, ConstructionPhaseRequest request);
}
