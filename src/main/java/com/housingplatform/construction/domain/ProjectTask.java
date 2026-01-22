package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_tasks")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ProjectTask extends BaseAuditEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private ConstructionPhase phase;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;
    
    @Column(name = "assigned_to")
    private UUID assignedTo;
    
    @Column(name = "due_date")
    private LocalDate dueDate;
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    @Column(name = "completed_date")
    private LocalDate completedDate;
    
    @Column(name = "estimated_hours", precision = 10, scale = 2)
    private BigDecimal estimatedHours;
    
    @Column(name = "actual_hours", precision = 10, scale = 2)
    private BigDecimal actualHours;
    
    @Column(name = "completion_percentage")
    @Builder.Default
    private Integer completionPercentage = 0;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private ProjectTask parentTask;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer sequence = 0;
    
    @Column(columnDefinition = "TEXT")
    private String tags;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    public enum TaskStatus {
        TODO, IN_PROGRESS, IN_REVIEW, COMPLETED, BLOCKED, CANCELLED
    }
    
    public enum TaskPriority {
        LOW, MEDIUM, HIGH, URGENT
    }
}
