package com.housingplatform.property.domain;

import com.housingplatform.identity.domain.User;
import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "property_messages")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PropertyMessage extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conversation_id", nullable = false)
  private PropertyConversation conversation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sender_user_id", nullable = false)
  private User sender;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "read_at")
  private LocalDateTime readAt;
}
