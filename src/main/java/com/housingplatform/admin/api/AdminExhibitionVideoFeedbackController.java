package com.housingplatform.admin.api;

import com.housingplatform.exhibition.dto.AdminVideoFeedbackResponse;
import com.housingplatform.exhibition.service.ExhibitionVideoFeedbackService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Admin moderation for exhibition visitor video feedback. */
@RestController
@RequestMapping("/api/v1/admin/exhibition/video-feedback")
@Tag(name = "Admin - Exhibition", description = "Moderate visitor video feedback")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminExhibitionVideoFeedbackController {

  private final ExhibitionVideoFeedbackService service;

  @GetMapping
  @Operation(summary = "List video feedback", description = "Optionally filter by status.")
  public ResponseEntity<Page<AdminVideoFeedbackResponse>> list(
      @RequestParam(value = "status", required = false) String status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(service.adminList(status, pageable));
  }

  @PutMapping("/{id}/approve")
  @Operation(summary = "Approve a video")
  public ResponseEntity<AdminVideoFeedbackResponse> approve(@PathVariable UUID id) {
    return ResponseEntity.ok(service.approve(id));
  }

  @PutMapping("/{id}/reject")
  @Operation(summary = "Reject a video")
  public ResponseEntity<AdminVideoFeedbackResponse> reject(@PathVariable UUID id) {
    return ResponseEntity.ok(service.reject(id));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a video (removes the file)")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
