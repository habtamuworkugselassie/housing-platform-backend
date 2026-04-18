package com.housingplatform.property.service;

import com.housingplatform.property.dto.ReviewDto;
import java.util.List;
import java.util.UUID;

public interface ReviewService {

  List<ReviewDto> getReviewsByPropertyId(UUID propertyId);

  List<ReviewDto> getReviewsByOrganizationId(UUID organizationId);

  ReviewDto createReview(UUID propertyId, ReviewDto reviewDto);

  ReviewDto createOrganizationReview(UUID organizationId, ReviewDto reviewDto);

  ReviewDto updatePropertyReview(
      UUID propertyId, UUID reviewId, UUID actorUserId, ReviewDto reviewDto);

  void deletePropertyReview(UUID propertyId, UUID reviewId, UUID actorUserId);

  ReviewDto updateOrganizationReview(
      UUID organizationId, UUID reviewId, UUID actorUserId, ReviewDto reviewDto);

  void deleteOrganizationReview(UUID organizationId, UUID reviewId, UUID actorUserId);
}
