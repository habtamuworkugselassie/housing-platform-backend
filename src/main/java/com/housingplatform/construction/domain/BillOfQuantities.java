package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "bills_of_quantities")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class BillOfQuantities extends BaseAuditEntity {

  @Column(name = "property_id")
  private UUID propertyId;

  @Column(name = "project_id", insertable = false, updatable = false)
  private UUID projectId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id")
  private ConstructionProject project;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BoQStatus status;

  @Column(precision = 19, scale = 2)
  private BigDecimal totalEstimatedCost;

  @OneToMany(mappedBy = "billOfQuantities", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<BoQItem> items = new ArrayList<>();

  public enum BoQStatus {
    DRAFT,
    APPROVED,
    IN_USE,
    ARCHIVED
  }
}
