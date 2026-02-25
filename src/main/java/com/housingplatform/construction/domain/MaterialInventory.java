package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "material_inventory")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class MaterialInventory extends BaseAuditEntity {

  @Column(name = "material_id", nullable = false)
  private UUID materialId; // Reference to material catalog

  @Column(name = "project_id")
  private UUID projectId; // Optional: project-specific inventory

  @Column(name = "warehouse_location")
  private String warehouseLocation; // Warehouse or storage location

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal quantity; // Current stock quantity

  @Column(name = "reserved_quantity", precision = 10, scale = 2)
  private BigDecimal reservedQuantity; // Reserved for orders/phases

  @Column(name = "available_quantity", nullable = false, precision = 10, scale = 2)
  private BigDecimal availableQuantity; // Available = quantity - reserved

  @Column(name = "minimum_stock_level", precision = 10, scale = 2)
  private BigDecimal minimumStockLevel; // Reorder point

  @Column(name = "maximum_stock_level", precision = 10, scale = 2)
  private BigDecimal maximumStockLevel;

  @Column(name = "unit_cost", precision = 19, scale = 2)
  private BigDecimal unitCost; // Average cost

  @Column(name = "total_value", precision = 19, scale = 2)
  private BigDecimal totalValue; // quantity * unitCost

  @Column(nullable = false)
  private String unit; // e.g., KG, TON, BAG, PIECE

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InventoryStatus status;

  @Column(columnDefinition = "TEXT")
  private String notes;

  public enum InventoryStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK,
    RESERVED,
    DAMAGED
  }
}
