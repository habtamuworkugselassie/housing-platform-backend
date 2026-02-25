package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.MaterialInventory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class MaterialInventoryRequest {

  @NotNull(message = "Material ID is required")
  private UUID materialId;

  private UUID projectId;

  @NotBlank(message = "Warehouse location is required")
  private String warehouseLocation;

  @NotNull(message = "Quantity is required")
  private BigDecimal quantity;

  private BigDecimal minimumStockLevel;

  private BigDecimal maximumStockLevel;

  private BigDecimal unitCost;

  @NotBlank(message = "Unit is required")
  private String unit;

  private MaterialInventory.InventoryStatus status;

  private String notes;
}
