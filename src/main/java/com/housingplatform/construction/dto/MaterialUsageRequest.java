package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.MaterialUsage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
public class MaterialUsageRequest {

  @NotNull(message = "Project ID is required")
  private UUID projectId;

  private UUID phaseId;

  @NotNull(message = "Material ID is required")
  private UUID materialId;

  private UUID inventoryId;

  private UUID orderId;

  @NotNull(message = "Quantity is required")
  @Positive(message = "Quantity must be positive")
  private BigDecimal quantity;

  @NotBlank(message = "Unit is required")
  private String unit;

  private BigDecimal unitCost;

  @NotNull(message = "Usage date is required")
  private LocalDate usageDate;

  @NotNull(message = "Usage type is required")
  private MaterialUsage.UsageType type;

  private String notes;
}
