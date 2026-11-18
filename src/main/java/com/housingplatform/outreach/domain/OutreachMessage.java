package com.housingplatform.outreach.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** What was actually sent to a contact, for the record. */
@Entity
@Table(name = "outreach_message")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class OutreachMessage extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contact_id")
  private OutreachContact contact;

  @Column(nullable = false)
  private int step;

  @Column(nullable = false, length = 300)
  private String subject;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "provider_message_id", length = 100)
  private String providerMessageId;

  @Column(name = "thread_id", length = 100)
  private String threadId;

  @Column(name = "sent_at", nullable = false)
  private LocalDateTime sentAt;
}
