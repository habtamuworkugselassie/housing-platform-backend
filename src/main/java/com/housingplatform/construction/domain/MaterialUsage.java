package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "material_usage")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class MaterialUsage extends BaseAuditEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "phase_id")
  private UUID phaseId; // Optional: specific phase

  @Column(name = "material_id", nullable = false)
  private UUID materialId;

  @Column(name = "inventory_id")
  private UUID inventoryId; // Reference to inventory entry

  @Column(name = "order_id")
  private UUID orderId; // Reference to order if applicable

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal quantity;

  @Column(nullable = false)
  private String unit;

  @Column(name = "unit_cost", precision = 19, scale = 2)
  private BigDecimal unitCost;

  @Column(name = "total_cost", precision = 19, scale = 2)
  private BigDecimal totalCost;

  @Column(name = "usage_date", nullable = false)
  private LocalDate usageDate;

  @Column(name = "used_by")
  private UUID usedBy; // User ID who recorded the usage

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UsageType type;

  public enum UsageType {
    CONSTRUCTION,
    REPAIR,
    MAINTENANCE,
    WASTE,
    RETURNED
  }
}
