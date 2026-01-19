package com.housingplatform.construction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BillOfQuantitiesRequest {
    
    @NotBlank(message = "BoQ name is required")
    private String name;
    
    private String description;
    
    private UUID propertyId;
    private UUID projectId;
    
    @NotNull(message = "Items are required")
    private List<BoQItemRequest> items;
}
