package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.BillOfQuantities;
import com.housingplatform.construction.domain.BoQItem;
import com.housingplatform.construction.dto.BillOfQuantitiesRequest;
import com.housingplatform.construction.dto.BillOfQuantitiesResponse;
import com.housingplatform.construction.dto.BoQItemRequest;
import com.housingplatform.construction.dto.BoQItemResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface BillOfQuantitiesMapper {
  BillOfQuantities toEntity(BillOfQuantitiesRequest request);

  BillOfQuantitiesResponse toResponse(BillOfQuantities boq);

  BoQItem toItemEntity(BoQItemRequest request);

  BoQItemResponse toItemResponse(BoQItem item);

  default List<BoQItemResponse> mapItems(List<BoQItem> items) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    return items.stream().map(this::toItemResponse).toList();
  }
}
