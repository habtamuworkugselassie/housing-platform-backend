package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "project_milestones")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ProjectMilestone extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id", nullable = false)
  private ConstructionProject project;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "target_date", nullable = false)
  private LocalDate targetDate;

  @Column(name = "actual_date")
  private LocalDate actualDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private MilestoneStatus status = MilestoneStatus.PENDING;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phase_id")
  private ConstructionPhase phase;

  @Column(name = "is_critical", nullable = false)
  @Builder.Default
  private Boolean isCritical = false;

  @Column(name = "completion_percentage")
  @Builder.Default
  private Integer completionPercentage = 0;

  @Column(columnDefinition = "TEXT")
  private String notes;

  public enum MilestoneStatus {
    PENDING,
    IN_PROGRESS,
    ACHIEVED,
    MISSED,
    CANCELLED
  }
}
