package com.housingplatform.property.api;

import com.housingplatform.property.dto.ReviewDto;
import com.housingplatform.property.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/reviews")
@Tag(name = "Property Reviews", description = "Reviews for property listings")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService reviewService;

  @GetMapping
  @Operation(summary = "List property reviews", description = "Returns all reviews for a property.")
  public ResponseEntity<List<ReviewDto>> getReviews(
      @Parameter(description = "Property id", required = true) @PathVariable UUID propertyId) {
    return ResponseEntity.ok(reviewService.getReviewsByPropertyId(propertyId));
  }

  @PostMapping
  @Operation(summary = "Create property review", description = "Adds a review for a property.")
  public ResponseEntity<ReviewDto> createReview(
      @Parameter(description = "Property id", required = true) @PathVariable UUID propertyId,
      @RequestBody ReviewDto reviewDto) {
    return ResponseEntity.ok(reviewService.createReview(propertyId, reviewDto));
  }
}
