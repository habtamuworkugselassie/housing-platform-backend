package com.housingplatform.audit.repository;

import com.housingplatform.audit.domain.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository
    extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
  List<AuditLog> findByUserId(UUID userId);

  List<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);

  List<AuditLog> findByAction(AuditLog.AuditAction action);

  List<AuditLog> findByActionTimestampBetween(LocalDateTime start, LocalDateTime end);
}
