package com.housingplatform.identity.api;

import com.housingplatform.property.dto.ReviewDto;
import com.housingplatform.property.service.ReviewService;
import com.housingplatform.shared.security.UserContext;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/reviews")
@Tag(name = "Organization Reviews", description = "Reviews for organizations")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class OrganizationReviewController {

  private final ReviewService reviewService;

  @GetMapping
  @Operation(
      summary = "List organization reviews",
      description = "Returns all reviews for an organization.")
  public ResponseEntity<List<ReviewDto>> getReviews(
      @Parameter(description = "Organization id", required = true) @PathVariable
          UUID organizationId) {
    return ResponseEntity.ok(reviewService.getReviewsByOrganizationId(organizationId));
  }

  @PostMapping
  @Operation(
      summary = "Create organization review",
      description = "Adds a review for an organization.")
  public ResponseEntity<ReviewDto> createReview(
      @Parameter(description = "Organization id", required = true) @PathVariable
          UUID organizationId,
      @RequestBody ReviewDto reviewDto) {
    return ResponseEntity.ok(reviewService.createOrganizationReview(organizationId, reviewDto));
  }

  @PutMapping("/{reviewId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(
      summary = "Update organization review",
      description =
          "Updates a review you submitted for this organization. Requires authentication.")
  public ResponseEntity<ReviewDto> updateReview(
      @Parameter(description = "Organization id", required = true) @PathVariable
          UUID organizationId,
      @Parameter(description = "Review id", required = true) @PathVariable UUID reviewId,
      @RequestBody ReviewDto reviewDto) {
    UUID actorId = UserContext.getCurrentUserId();
    return ResponseEntity.ok(
        reviewService.updateOrganizationReview(organizationId, reviewId, actorId, reviewDto));
  }

  @DeleteMapping("/{reviewId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete organization review",
      description =
          "Deletes a review you submitted for this organization. Requires authentication.")
  public void deleteReview(
      @Parameter(description = "Organization id", required = true) @PathVariable
          UUID organizationId,
      @Parameter(description = "Review id", required = true) @PathVariable UUID reviewId) {
    UUID actorId = UserContext.getCurrentUserId();
    reviewService.deleteOrganizationReview(organizationId, reviewId, actorId);
  }
}
