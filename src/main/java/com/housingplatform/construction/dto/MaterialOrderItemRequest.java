package com.housingplatform.construction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class MaterialOrderItemRequest {
    
    private UUID materialId; // Optional: if ordering from catalog
    
    @NotBlank(message = "Material name is required")
    private String materialName;
    
    private String description;
    
    @NotBlank(message = "Category is required")
    private String category;
    
    @NotBlank(message = "Unit is required")
    private String unit;
    
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;
    
    @NotNull(message = "Unit price is required")
    @Positive(message = "Unit price must be positive")
    private BigDecimal unitPrice;
    
    private String brand;
    
    private String specifications;
    
    private Integer sequence;
}
