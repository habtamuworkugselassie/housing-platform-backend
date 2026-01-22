package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_issues")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ProjectIssue extends BaseAuditEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ConstructionProject project;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id")
    private ConstructionPhase phase;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IssueType type = IssueType.ISSUE;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IssueSeverity severity = IssueSeverity.MEDIUM;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IssueStatus status = IssueStatus.OPEN;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IssuePriority priority = IssuePriority.MEDIUM;
    
    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;
    
    @Column(name = "assigned_to")
    private UUID assignedTo;
    
    @Column(name = "due_date")
    private LocalDate dueDate;
    
    @Column(name = "resolved_date")
    private LocalDate resolvedDate;
    
    @Column(columnDefinition = "TEXT")
    private String resolution;
    
    @Column(columnDefinition = "TEXT")
    private String impact;
    
    @Column(name = "mitigation_plan", columnDefinition = "TEXT")
    private String mitigationPlan;
    
    public enum IssueType {
        ISSUE, RISK, BLOCKER, BUG, CHANGE_REQUEST
    }
    
    public enum IssueSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum IssueStatus {
        OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED
    }
    
    public enum IssuePriority {
        LOW, MEDIUM, HIGH, URGENT
    }
}
