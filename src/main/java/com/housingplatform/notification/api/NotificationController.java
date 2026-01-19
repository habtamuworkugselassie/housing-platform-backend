package com.housingplatform.notification.api;

import com.housingplatform.notification.dto.NotificationResponse;
import com.housingplatform.notification.service.NotificationService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import com.housingplatform.shared.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Notification management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class NotificationController {
    
    private final NotificationService notificationService;
    
    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieve all notifications for the current user")
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID userId = UserContext.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationResponse> notifications = notificationService.getUserNotifications(userId, isRead, pageable);
        return ResponseEntity.ok(notifications);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get notification by ID", description = "Retrieve notification information by ID")
    public ResponseEntity<NotificationResponse> getNotificationById(@PathVariable UUID id) {
        NotificationResponse notification = notificationService.getNotificationById(id);
        return ResponseEntity.ok(notification);
    }
    
    @PutMapping("/{id}/read")
    @Operation(summary = "Mark notification as read", description = "Mark a notification as read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        UUID userId = UserContext.getCurrentUserId();
        notificationService.markAsRead(userId, id);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Mark all user notifications as read")
    public ResponseEntity<Void> markAllAsRead() {
        UUID userId = UserContext.getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/unread-count")
    @Operation(summary = "Get unread count", description = "Get count of unread notifications for the current user")
    public ResponseEntity<Long> getUnreadCount() {
        UUID userId = UserContext.getCurrentUserId();
        Long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }
}
