package com.housingplatform.property.service;

import com.housingplatform.property.dto.ReviewDto;
import java.util.List;
import java.util.UUID;

public interface ReviewService {

  List<ReviewDto> getReviewsByPropertyId(UUID propertyId);

  ReviewDto createReview(UUID propertyId, ReviewDto reviewDto);
}
