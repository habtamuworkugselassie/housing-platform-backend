package com.housingplatform.exhibition.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * One row per lifecycle mail we have tried to send to one registrant.
 *
 * <p>A unique index on {@code (interest_id, email_kind)} makes this the send log *and* the lock: a
 * second attempt at the same mail updates this row instead of inserting another. That is what lets
 * the daily job be safely rerun, and what makes a redeploy, a restart or an operator running the
 * job by hand harmless. A failed attempt stays retryable; a successful one never repeats.
 */
@Entity
@Table(name = "exhibition_interest_email")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ExhibitionInterestEmail extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "interest_id", nullable = false)
  private ExhibitionInterest interest;

  @Enumerated(EnumType.STRING)
  @Column(name = "email_kind", nullable = false, length = 32)
  private ExhibitionEmailKind kind;

  /** The address actually mailed, kept even if the lead later changes theirs. */
  @Column(nullable = false)
  private String recipient;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(nullable = false)
  @Builder.Default
  private Integer attempts = 0;

  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  /** Failure reason, or why the mail was suppressed. Truncated to fit the column. */
  @Column(length = 500)
  private String detail;

  public enum Status {
    SENT,
    FAILED,

    /**
     * Deliberately not sent, and not to be retried — mail is switched off, or the reminder came due
     * after the registrant had opted out. Recorded rather than skipped silently so the log answers
     * "why did this person not get the T-7 mail?" without guesswork.
     */
    SUPPRESSED
  }
}
