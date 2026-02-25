package com.housingplatform.audit.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AuditLog extends BaseEntity {

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "entity_type", nullable = false)
  private String entityType; // e.g., Property, LoanApplication, Payment

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuditAction action;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(columnDefinition = "TEXT")
  private String oldValue; // JSON representation of old state

  @Column(columnDefinition = "TEXT")
  private String newValue; // JSON representation of new state

  @Column(nullable = false)
  private String ipAddress;

  @Column(nullable = false)
  private String userAgent;

  @Column(nullable = false)
  private LocalDateTime actionTimestamp;

  public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    VIEW,
    APPROVE,
    REJECT,
    DISBURSE
  }
}
