package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.MaterialInventory;
import com.housingplatform.construction.dto.MaterialInventoryRequest;
import com.housingplatform.construction.dto.MaterialInventoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MaterialInventoryMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reservedQuantity", ignore = true)
    @Mapping(target = "availableQuantity", ignore = true)
    @Mapping(target = "totalValue", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    MaterialInventory toEntity(MaterialInventoryRequest request);
    
    @Mapping(target = "materialName", ignore = true)
    @Mapping(target = "projectName", ignore = true)
    MaterialInventoryResponse toResponse(MaterialInventory inventory);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reservedQuantity", ignore = true)
    @Mapping(target = "availableQuantity", ignore = true)
    @Mapping(target = "totalValue", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget MaterialInventory inventory, MaterialInventoryRequest request);
}
