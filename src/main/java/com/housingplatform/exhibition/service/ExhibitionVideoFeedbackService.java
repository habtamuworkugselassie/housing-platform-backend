package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.dto.AdminVideoFeedbackResponse;
import com.housingplatform.exhibition.dto.VideoFeedbackResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ExhibitionVideoFeedbackService {

  /**
   * Anonymous visitor submits a short video. Whether it is published immediately or held for review
   * is decided by the exhibitionFeedbackAutoPublish display setting.
   */
  VideoFeedbackResponse submit(
      String submitterName, String submitterEmail, String caption, MultipartFile file, String ip);

  /** Public feed: approved clips only. */
  Page<VideoFeedbackResponse> listApproved(Pageable pageable);

  /** Admin moderation list; when status is null, returns all. */
  Page<AdminVideoFeedbackResponse> adminList(String status, Pageable pageable);

  AdminVideoFeedbackResponse approve(UUID id);

  AdminVideoFeedbackResponse reject(UUID id);

  void delete(UUID id);
}
