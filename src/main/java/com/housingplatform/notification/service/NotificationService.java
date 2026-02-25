package com.housingplatform.notification.service;

import com.housingplatform.notification.dto.NotificationResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
  Page<NotificationResponse> getUserNotifications(UUID userId, Boolean isRead, Pageable pageable);

  NotificationResponse getNotificationById(UUID id);

  void markAsRead(UUID userId, UUID notificationId);

  void markAllAsRead(UUID userId);

  Long getUnreadCount(UUID userId);
}
