package com.housingplatform.notification.service.impl;

import com.housingplatform.notification.domain.Notification;
import com.housingplatform.notification.dto.NotificationResponse;
import com.housingplatform.notification.repository.NotificationRepository;
import com.housingplatform.notification.service.NotificationMapper;
import com.housingplatform.notification.service.NotificationService;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;

  @Override
  @Transactional(readOnly = true)
  public Page<NotificationResponse> getUserNotifications(
      UUID userId, Boolean isRead, Pageable pageable) {
    Specification<Notification> spec =
        Specification.where((root, query, cb) -> cb.equal(root.get("userId"), userId));

    if (isRead != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("isRead"), isRead));
    }

    return notificationRepository.findAll(spec, pageable).map(notificationMapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public NotificationResponse getNotificationById(UUID id) {
    Notification notification =
        notificationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    return notificationMapper.toResponse(notification);
  }

  @Override
  public void markAsRead(UUID userId, UUID notificationId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

    if (!notification.getUserId().equals(userId)) {
      throw new IllegalArgumentException("Notification does not belong to the user");
    }

    notification.setIsRead(true);
    notification.setReadAt(LocalDateTime.now());
    notificationRepository.save(notification);
  }

  @Override
  public void markAllAsRead(UUID userId) {
    notificationRepository.markAllAsRead(userId, LocalDateTime.now());
  }

  @Override
  @Transactional(readOnly = true)
  public Long getUnreadCount(UUID userId) {
    return notificationRepository.countByUserIdAndIsReadFalse(userId);
  }
}
