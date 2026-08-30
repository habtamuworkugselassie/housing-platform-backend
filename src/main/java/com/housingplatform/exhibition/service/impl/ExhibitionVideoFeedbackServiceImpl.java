package com.housingplatform.exhibition.service.impl;

import com.housingplatform.exhibition.domain.ExhibitionVideoFeedback;
import com.housingplatform.exhibition.domain.ExhibitionVideoFeedback.FeedbackStatus;
import com.housingplatform.exhibition.domain.ExhibitionVideoFeedback.SubmitterRole;
import com.housingplatform.exhibition.dto.AdminVideoFeedbackResponse;
import com.housingplatform.exhibition.dto.VideoFeedbackResponse;
import com.housingplatform.exhibition.repository.ExhibitionVideoFeedbackRepository;
import com.housingplatform.exhibition.service.ExhibitionVideoFeedbackService;
import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.service.DisplaySettingsService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ExhibitionVideoFeedbackServiceImpl implements ExhibitionVideoFeedbackService {

  private static final long MAX_VIDEO_SIZE = 60L * 1024 * 1024; // 60MB — short clips only
  private static final String VIDEO_SUBPATH = "exhibition/feedback/videos";
  private static final int RATE_LIMIT_WINDOW_MINUTES = 10;
  private static final int RATE_LIMIT_MAX_PER_WINDOW = 5;

  private final ExhibitionVideoFeedbackRepository repository;
  private final MediaStorageService mediaStorageService;
  private final DisplaySettingsService displaySettingsService;

  @Override
  @Transactional
  public VideoFeedbackResponse submit(
      String submitterName,
      String submitterEmail,
      String submitterRole,
      String companyName,
      String caption,
      MultipartFile file,
      String ip) {
    String name = submitterName == null ? "" : submitterName.trim();
    String email = submitterEmail == null ? "" : submitterEmail.trim();
    if (name.isBlank()) {
      throw new BusinessException("Your name is required.");
    }
    if (email.isBlank() || !email.contains("@")) {
      throw new BusinessException("A valid email is required.");
    }
    validateVideo(file);

    if (ip != null && !ip.isBlank()) {
      long recent =
          repository.countBySubmitterIpAndCreatedAtAfter(
              ip, LocalDateTime.now().minusMinutes(RATE_LIMIT_WINDOW_MINUTES));
      if (recent >= RATE_LIMIT_MAX_PER_WINDOW) {
        throw new BusinessException(
            "Too many submissions from this device. Please try again later.");
      }
    }

    String videoUrl = mediaStorageService.save(file, VIDEO_SUBPATH);

    boolean autoPublish = displaySettingsService.getDisplaySettings().isExhibitionFeedbackAutoPublish();

    ExhibitionVideoFeedback saved =
        repository.save(
            ExhibitionVideoFeedback.builder()
                .submitterName(name)
                .submitterEmail(email)
                .submitterRole(parseRole(submitterRole))
                .companyName(
                    companyName == null || companyName.isBlank() ? null : companyName.trim())
                .caption(caption == null ? null : caption.trim())
                .videoUrl(videoUrl)
                .contentType(file.getContentType())
                .submitterIp(ip)
                .status(autoPublish ? FeedbackStatus.APPROVED : FeedbackStatus.PENDING)
                .build());
    return VideoFeedbackResponse.from(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<VideoFeedbackResponse> listApproved(Pageable pageable) {
    return repository
        .findByStatus(FeedbackStatus.APPROVED, pageable)
        .map(VideoFeedbackResponse::from);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AdminVideoFeedbackResponse> adminList(String status, Pageable pageable) {
    FeedbackStatus parsed = parseStatus(status);
    Page<ExhibitionVideoFeedback> page =
        parsed == null
            ? repository.findAll(pageable)
            : repository.findByStatus(parsed, pageable);
    return page.map(AdminVideoFeedbackResponse::from);
  }

  @Override
  @Transactional
  public AdminVideoFeedbackResponse approve(UUID id) {
    return setStatus(id, FeedbackStatus.APPROVED);
  }

  @Override
  @Transactional
  public AdminVideoFeedbackResponse reject(UUID id) {
    return setStatus(id, FeedbackStatus.REJECTED);
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    ExhibitionVideoFeedback f =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Video feedback", id));
    mediaStorageService.deleteByUrl(f.getVideoUrl());
    repository.delete(f);
  }

  private AdminVideoFeedbackResponse setStatus(UUID id, FeedbackStatus status) {
    ExhibitionVideoFeedback f =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Video feedback", id));
    f.setStatus(status);
    return AdminVideoFeedbackResponse.from(repository.save(f));
  }

  private static void validateVideo(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException("A video file is required.");
    }
    if (file.getSize() > MAX_VIDEO_SIZE) {
      throw new BusinessException("Video exceeds the maximum size of 60MB.");
    }
    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("video/")) {
      throw new BusinessException("The uploaded file must be a video.");
    }
  }

  private static SubmitterRole parseRole(String role) {
    return "EXHIBITOR".equalsIgnoreCase(role == null ? "" : role.trim())
        ? SubmitterRole.EXHIBITOR
        : SubmitterRole.VISITOR;
  }

  private static FeedbackStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return FeedbackStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BusinessException("Unknown status: " + status);
    }
  }
}
