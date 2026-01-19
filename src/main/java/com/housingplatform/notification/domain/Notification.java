package com.housingplatform.notification.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Notification extends BaseEntity {
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;
    
    private LocalDateTime readAt;
    
    @Column(columnDefinition = "TEXT")
    private String actionUrl; // Optional: link to related resource
    
    public enum NotificationType {
        LOAN_STATUS_UPDATE, PROPERTY_VERIFICATION, PAYMENT_RECEIVED, SYSTEM_ALERT
    }
    
    public enum NotificationStatus {
        PENDING, SENT, FAILED
    }
}
