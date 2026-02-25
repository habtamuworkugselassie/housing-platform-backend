package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "boq_items")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class BoQItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bill_of_quantities_id", nullable = false)
  private BillOfQuantities billOfQuantities;

  @Column(nullable = false)
  private String itemName;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private String unit; // e.g., KG, TON, BAG, PIECE, M2

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal quantity;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal totalPrice;

  @Column(name = "material_id")
  private UUID materialId; // Optional: link to supplier material catalog

  private Integer sequence;
}
