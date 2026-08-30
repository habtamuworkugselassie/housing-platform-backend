package com.housingplatform.exhibition.dto;

import com.housingplatform.exhibition.domain.ExhibitionVideoFeedback;
import java.time.LocalDateTime;
import java.util.UUID;

/** Public view of an approved video feedback clip (no email / IP). */
public record VideoFeedbackResponse(
    UUID id,
    String submitterName,
    String submitterRole,
    String companyName,
    String caption,
    String videoUrl,
    LocalDateTime createdAt) {

  public static VideoFeedbackResponse from(ExhibitionVideoFeedback f) {
    return new VideoFeedbackResponse(
        f.getId(),
        f.getSubmitterName(),
        f.getSubmitterRole() == null ? null : f.getSubmitterRole().name(),
        f.getCompanyName(),
        f.getCaption(),
        f.getVideoUrl(),
        f.getCreatedAt());
  }
}
