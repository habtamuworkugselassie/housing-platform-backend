package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.MaterialOrder;
import com.housingplatform.construction.domain.MaterialOrderItem;
import com.housingplatform.construction.dto.MaterialOrderRequest;
import com.housingplatform.construction.dto.MaterialOrderItemRequest;
import com.housingplatform.construction.dto.MaterialOrderResponse;
import com.housingplatform.construction.dto.MaterialOrderItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDate;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, imports = {LocalDate.class})
public interface MaterialOrderMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "orderDate", expression = "java(request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now())")
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    MaterialOrder toEntity(MaterialOrderRequest request);
    
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    @Mapping(target = "supplierName", ignore = true)
    @Mapping(target = "orderedByName", ignore = true)
    MaterialOrderResponse toResponse(MaterialOrder order);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "receivedQuantity", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    MaterialOrderItem toItemEntity(MaterialOrderItemRequest request);
    
    @Mapping(target = "orderId", source = "order.id")
    MaterialOrderItemResponse toItemResponse(MaterialOrderItem item);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget MaterialOrder order, MaterialOrderRequest request);
}
