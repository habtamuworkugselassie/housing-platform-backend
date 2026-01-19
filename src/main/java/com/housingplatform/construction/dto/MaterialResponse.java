package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.Material;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialResponse {
    private UUID id;
    private UUID supplierId;
    private String name;
    private String description;
    private String category;
    private String unit;
    private BigDecimal unitPrice;
    private Integer availableQuantity;
    private Material.MaterialStatus status;
    private String brand;
    private String specifications;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
