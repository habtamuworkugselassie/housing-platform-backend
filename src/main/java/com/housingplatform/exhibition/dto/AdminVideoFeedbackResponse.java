package com.housingplatform.exhibition.dto;

import com.housingplatform.exhibition.domain.ExhibitionVideoFeedback;
import java.time.LocalDateTime;
import java.util.UUID;

/** Admin/moderation view of a video feedback clip (includes email, status, IP). */
public record AdminVideoFeedbackResponse(
    UUID id,
    String submitterName,
    String submitterEmail,
    String submitterRole,
    String companyName,
    String caption,
    String videoUrl,
    String contentType,
    String status,
    String submitterIp,
    LocalDateTime createdAt) {

  public static AdminVideoFeedbackResponse from(ExhibitionVideoFeedback f) {
    return new AdminVideoFeedbackResponse(
        f.getId(),
        f.getSubmitterName(),
        f.getSubmitterEmail(),
        f.getSubmitterRole() == null ? null : f.getSubmitterRole().name(),
        f.getCompanyName(),
        f.getCaption(),
        f.getVideoUrl(),
        f.getContentType(),
        f.getStatus() == null ? null : f.getStatus().name(),
        f.getSubmitterIp(),
        f.getCreatedAt());
  }
}
