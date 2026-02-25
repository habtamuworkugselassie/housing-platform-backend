package com.housingplatform.banking.service;

import com.housingplatform.banking.domain.CreditProduct;
import com.housingplatform.banking.dto.CreditProductRequest;
import com.housingplatform.banking.dto.CreditProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CreditProductMapper {
  CreditProduct toEntity(CreditProductRequest request);

  CreditProductResponse toResponse(CreditProduct product);

  void updateEntity(@MappingTarget CreditProduct product, CreditProductRequest request);
}
