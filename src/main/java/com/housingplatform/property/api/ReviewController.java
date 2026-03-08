package com.housingplatform.property.api;

import com.housingplatform.property.dto.ReviewDto;
import com.housingplatform.property.service.ReviewService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService reviewService;

  @GetMapping
  public ResponseEntity<List<ReviewDto>> getReviews(@PathVariable UUID propertyId) {
    return ResponseEntity.ok(reviewService.getReviewsByPropertyId(propertyId));
  }

  @PostMapping
  public ResponseEntity<ReviewDto> createReview(
      @PathVariable UUID propertyId, @RequestBody ReviewDto reviewDto) {
    return ResponseEntity.ok(reviewService.createReview(propertyId, reviewDto));
  }
}
