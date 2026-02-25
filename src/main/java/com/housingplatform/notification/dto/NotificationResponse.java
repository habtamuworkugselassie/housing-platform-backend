package com.housingplatform.notification.dto;

import com.housingplatform.notification.domain.Notification;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
  private UUID id;
  private UUID userId;
  private String title;
  private String message;
  private Notification.NotificationType type;
  private Notification.NotificationStatus status;
  private Boolean isRead;
  private LocalDateTime readAt;
  private String actionUrl;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
