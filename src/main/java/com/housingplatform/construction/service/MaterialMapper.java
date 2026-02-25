package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.Material;
import com.housingplatform.construction.dto.MaterialRequest;
import com.housingplatform.construction.dto.MaterialResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MaterialMapper {
  Material toEntity(MaterialRequest request);

  MaterialResponse toResponse(Material material);

  void updateEntity(@MappingTarget Material material, MaterialRequest request);
}
