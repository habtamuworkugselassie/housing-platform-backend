package com.housingplatform.outreach.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** One organization in the curated outreach list and where it is in the sequence. */
@Entity
@Table(name = "outreach_contact")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class OutreachContact extends BaseEntity {

  public enum Status {
    /** Imported, not yet scheduled. */
    NEW,
    /** Will be sent the first email at the next free slot. */
    QUEUED,
    /** First email sent; follow-up due at nextActionAt unless they reply. */
    SENT,
    /** Follow-up sent; sequence finished. */
    FOLLOWED_UP,
    REPLIED,
    REGISTERED,
    OPTED_OUT,
    BOUNCED,
    /** Held by an admin; nothing is sent until queued again. */
    PAUSED
  }

  @Column(nullable = false, length = 300)
  private String name;

  @Column(name = "contact_name", length = 200)
  private String contactName;

  @Column(nullable = false, length = 100)
  private String sector;

  /** Overrides the sector-derived template when set (one of TemplateKey). */
  @Column(name = "template_key", length = 40)
  private String templateKey;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(length = 100)
  private String phone;

  @Column(length = 500)
  private String website;

  @Column(length = 200)
  private String city;

  @Column(nullable = false, length = 8)
  @Builder.Default
  private String locale = "en";

  @Column(length = 100)
  private String confidence;

  @Column(name = "source_url", length = 1000)
  private String sourceUrl;

  @Column(columnDefinition = "TEXT")
  private String notes;

  /** The personal opening line: what of theirs you noticed. Required before sending. */
  @Column(length = 500)
  private String hook;

  @Column(length = 100)
  private String owner;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  @Builder.Default
  private Status status = Status.NEW;

  @Column(nullable = false)
  @Builder.Default
  private int step = 0;

  @Column(name = "next_action_at")
  private LocalDateTime nextActionAt;

  @Column(name = "last_sent_at")
  private LocalDateTime lastSentAt;

  @Column(name = "replied_at")
  private LocalDateTime repliedAt;

  /** Gmail thread the conversation lives in; replies are detected on it. */
  @Column(name = "thread_id", length = 100)
  private String threadId;

  @Column(name = "last_error", length = 1000)
  private String lastError;
}
