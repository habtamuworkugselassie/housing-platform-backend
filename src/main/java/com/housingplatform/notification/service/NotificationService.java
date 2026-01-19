package com.housingplatform.notification.service;

import com.housingplatform.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {
    Page<NotificationResponse> getUserNotifications(UUID userId, Boolean isRead, Pageable pageable);
    NotificationResponse getNotificationById(UUID id);
    void markAsRead(UUID userId, UUID notificationId);
    void markAllAsRead(UUID userId);
    Long getUnreadCount(UUID userId);
}
