package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.BillOfQuantities;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillOfQuantitiesResponse {
    private UUID id;
    private UUID propertyId;
    private UUID projectId;
    private String name;
    private String description;
    private BillOfQuantities.BoQStatus status;
    private BigDecimal totalEstimatedCost;
    private List<BoQItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
