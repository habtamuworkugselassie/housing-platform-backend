package com.housingplatform.exhibition.api;

import com.housingplatform.exhibition.dto.VideoFeedbackResponse;
import com.housingplatform.exhibition.service.ExhibitionVideoFeedbackService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Public visitor video-feedback: anonymous submit + approved feed. */
@RestController
@RequestMapping("/api/v1/exhibition/video-feedback")
@Tag(name = "Exhibition", description = "Exhibition visitor video feedback (public)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class ExhibitionVideoFeedbackController {

  private final ExhibitionVideoFeedbackService service;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Submit a video testimonial",
      description =
          "Anonymous, public. Whether the clip is published immediately or held for moderation is"
              + " controlled by the exhibitionFeedbackAutoPublish display setting.")
  public ResponseEntity<VideoFeedbackResponse> submit(
      @RequestParam("name") String name,
      @RequestParam("email") String email,
      @RequestParam(value = "role", required = false) String role,
      @RequestParam(value = "company", required = false) String company,
      @RequestParam(value = "caption", required = false) String caption,
      @RequestParam("file") MultipartFile file,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.submit(name, email, role, company, caption, file, clientIp(request)));
  }

  @GetMapping
  @Operation(summary = "List approved video feedback", description = "Public feed of approved clips.")
  public ResponseEntity<Page<VideoFeedbackResponse>> listApproved(
      @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(service.listApproved(pageable));
  }

  private static String clientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }
}
