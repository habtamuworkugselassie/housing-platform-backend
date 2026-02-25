package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "construction_phases")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ConstructionPhase extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id", nullable = false)
  private ConstructionProject project;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PhaseStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PhaseType type;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "planned_end_date")
  private LocalDate plannedEndDate;

  @Column(name = "actual_end_date")
  private LocalDate actualEndDate;

  @Column(name = "completion_percentage")
  private Integer completionPercentage; // 0-100

  @Column(precision = 19, scale = 2)
  private BigDecimal budget;

  @Column(name = "actual_cost", precision = 19, scale = 2)
  private BigDecimal actualCost;

  @Column(nullable = false)
  private Integer sequence; // Order of phases

  @Column(columnDefinition = "TEXT")
  private String notes;

  public enum PhaseStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    ON_HOLD,
    CANCELLED
  }

  public enum PhaseType {
    SITE_PREPARATION,
    EXCAVATION,
    FOUNDATION,
    FRAMING,
    ROOFING,
    ELECTRICAL,
    PLUMBING,
    HVAC,
    INSULATION,
    DRYWALL,
    PAINTING,
    FLOORING,
    FINISHING,
    LANDSCAPING,
    INSPECTION,
    OTHER
  }
}
