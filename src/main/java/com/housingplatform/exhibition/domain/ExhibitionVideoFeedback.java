package com.housingplatform.exhibition.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A short video testimonial submitted by an (anonymous) exhibition visitor. Whether a new
 * submission is published immediately or held for moderation is decided at submission time by the
 * {@code exhibitionFeedbackAutoPublish} display setting.
 */
@Entity
@Table(name = "exhibition_video_feedback")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ExhibitionVideoFeedback extends BaseEntity {

  @Column(name = "submitter_name", nullable = false, length = 120)
  private String submitterName;

  @Column(name = "submitter_email", nullable = false, length = 255)
  private String submitterEmail;

  @Column(columnDefinition = "TEXT")
  private String caption;

  /** Public URL path of the stored video (e.g. /api/v1/uploads/exhibition/feedback/videos/…). */
  @Column(name = "video_url", nullable = false, length = 512)
  private String videoUrl;

  @Column(name = "content_type", length = 100)
  private String contentType;

  /** Best-effort client IP, kept for rate limiting / abuse review; never exposed publicly. */
  @Column(name = "submitter_ip", length = 64)
  private String submitterIp;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FeedbackStatus status;

  public enum FeedbackStatus {
    PENDING,
    APPROVED,
    REJECTED
  }
}
