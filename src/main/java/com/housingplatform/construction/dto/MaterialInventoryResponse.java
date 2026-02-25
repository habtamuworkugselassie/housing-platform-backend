package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.MaterialInventory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class MaterialInventoryResponse {

  private UUID id;

  private UUID materialId;

  private String materialName;

  private UUID projectId;

  private String projectName;

  private String warehouseLocation;

  private BigDecimal quantity;

  private BigDecimal reservedQuantity;

  private BigDecimal availableQuantity;

  private BigDecimal minimumStockLevel;

  private BigDecimal maximumStockLevel;

  private BigDecimal unitCost;

  private BigDecimal totalValue;

  private String unit;

  private MaterialInventory.InventoryStatus status;

  private String notes;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
