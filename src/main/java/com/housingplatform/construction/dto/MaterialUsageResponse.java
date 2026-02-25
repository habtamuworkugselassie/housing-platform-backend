package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.MaterialUsage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class MaterialUsageResponse {

  private UUID id;

  private UUID projectId;

  private String projectName;

  private UUID phaseId;

  private String phaseName;

  private UUID materialId;

  private String materialName;

  private UUID inventoryId;

  private UUID orderId;

  private String orderNumber;

  private BigDecimal quantity;

  private String unit;

  private BigDecimal unitCost;

  private BigDecimal totalCost;

  private LocalDate usageDate;

  private UUID usedBy;

  private String usedByName;

  private MaterialUsage.UsageType type;

  private String notes;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
