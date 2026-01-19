package com.housingplatform.notification.repository;

import com.housingplatform.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {
    List<Notification> findByUserId(UUID userId);
    List<Notification> findByUserIdAndIsRead(UUID userId, Boolean isRead);
    List<Notification> findByStatus(Notification.NotificationStatus status);
    Long countByUserIdAndIsReadFalse(UUID userId);
    
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsRead(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);
}
