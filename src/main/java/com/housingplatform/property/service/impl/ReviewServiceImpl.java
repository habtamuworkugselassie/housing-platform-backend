package com.housingplatform.property.service.impl;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.media.domain.MediaAttachment;
import com.housingplatform.media.repository.MediaAttachmentRepository;
import com.housingplatform.media.util.UserProfileMediaUrls;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.domain.Review;
import com.housingplatform.property.dto.ReviewDto;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.property.repository.ReviewRepository;
import com.housingplatform.property.service.ReviewService;
import com.housingplatform.publicsupport.rag.SupportRagIndexEvents;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

  private final ReviewRepository reviewRepository;
  private final PropertyRepository propertyRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final MediaAttachmentRepository mediaAttachmentRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional(readOnly = true)
  public List<ReviewDto> getReviewsByPropertyId(UUID propertyId) {
    return reviewRepository.findByPropertyId(propertyId).stream()
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public ReviewDto createReview(UUID propertyId, ReviewDto reviewDto) {

    User user = null;
    if (reviewDto.getUserId() != null) {
      user =
          userRepository
              .findById(reviewDto.getUserId())
              .orElseThrow(() -> new RuntimeException("User not found"));
    }

    Property property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new RuntimeException("Property not found"));

    Review.ReviewBuilder<?, ?> builder =
        Review.builder()
            .property(property)
            .user(user)
            .rating(reviewDto.getRating())
            .comment(reviewDto.getComment());

    if (reviewDto.getCreatedAt() != null) {
      builder.createdAt(reviewDto.getCreatedAt());
    }

    Review review = builder.build();

    Review savedReview = reviewRepository.save(review);
    eventPublisher.publishEvent(
        new SupportRagIndexEvents.RagIndexPropertyEvent(savedReview.getProperty().getId()));
    return toDto(savedReview);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ReviewDto> getReviewsByOrganizationId(UUID organizationId) {
    return reviewRepository.findByOrganizationId(organizationId).stream()
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public ReviewDto createOrganizationReview(UUID organizationId, ReviewDto reviewDto) {

    User user = null;
    if (reviewDto.getUserId() != null) {
      user =
          userRepository
              .findById(reviewDto.getUserId())
              .orElseThrow(() -> new RuntimeException("User not found"));
    }

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new RuntimeException("Organization not found"));

    Review.ReviewBuilder<?, ?> builder =
        Review.builder()
            .organization(organization)
            .user(user)
            .rating(reviewDto.getRating())
            .comment(reviewDto.getComment());

    if (reviewDto.getCreatedAt() != null) {
      builder.createdAt(reviewDto.getCreatedAt());
    }

    Review review = builder.build();

    Review savedReview = reviewRepository.save(review);
    eventPublisher.publishEvent(
        new SupportRagIndexEvents.RagIndexOrganizationEvent(savedReview.getOrganization().getId()));
    return toDto(savedReview);
  }

  @Override
  @Transactional
  public ReviewDto updatePropertyReview(
      UUID propertyId, UUID reviewId, UUID actorUserId, ReviewDto reviewDto) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new RuntimeException("Review not found"));
    if (review.getProperty() == null || !review.getProperty().getId().equals(propertyId)) {
      throw new RuntimeException("Review not found");
    }
    assertActorOwnsReview(review, actorUserId);
    applyReviewUpdate(review, reviewDto);
    Review saved = reviewRepository.save(review);
    eventPublisher.publishEvent(new SupportRagIndexEvents.RagIndexPropertyEvent(propertyId));
    return toDto(saved);
  }

  @Override
  @Transactional
  public void deletePropertyReview(UUID propertyId, UUID reviewId, UUID actorUserId) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new RuntimeException("Review not found"));
    if (review.getProperty() == null || !review.getProperty().getId().equals(propertyId)) {
      throw new RuntimeException("Review not found");
    }
    assertActorOwnsReview(review, actorUserId);
    reviewRepository.delete(review);
    eventPublisher.publishEvent(new SupportRagIndexEvents.RagIndexPropertyEvent(propertyId));
  }

  @Override
  @Transactional
  public ReviewDto updateOrganizationReview(
      UUID organizationId, UUID reviewId, UUID actorUserId, ReviewDto reviewDto) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new RuntimeException("Review not found"));
    if (review.getOrganization() == null
        || !review.getOrganization().getId().equals(organizationId)) {
      throw new RuntimeException("Review not found");
    }
    assertActorOwnsReview(review, actorUserId);
    applyReviewUpdate(review, reviewDto);
    Review saved = reviewRepository.save(review);
    eventPublisher.publishEvent(
        new SupportRagIndexEvents.RagIndexOrganizationEvent(organizationId));
    return toDto(saved);
  }

  @Override
  @Transactional
  public void deleteOrganizationReview(UUID organizationId, UUID reviewId, UUID actorUserId) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new RuntimeException("Review not found"));
    if (review.getOrganization() == null
        || !review.getOrganization().getId().equals(organizationId)) {
      throw new RuntimeException("Review not found");
    }
    assertActorOwnsReview(review, actorUserId);
    reviewRepository.delete(review);
    eventPublisher.publishEvent(
        new SupportRagIndexEvents.RagIndexOrganizationEvent(organizationId));
  }

  private static void assertActorOwnsReview(Review review, UUID actorUserId) {
    if (review.getUser() == null) {
      throw new RuntimeException("Anonymous reviews cannot be modified");
    }
    if (actorUserId == null || !review.getUser().getId().equals(actorUserId)) {
      throw new RuntimeException("Not authorized to modify this review");
    }
  }

  private static void applyReviewUpdate(Review review, ReviewDto reviewDto) {
    review.setRating(reviewDto.getRating());
    review.setComment(reviewDto.getComment());
  }

  private ReviewDto toDto(Review review) {

    User user = review.getUser();
    if (user != null) {
      List<MediaAttachment> attachments =
          mediaAttachmentRepository.findByUserIdOrderByDisplayOrderAsc(user.getId());
      String imageUrl = null;

      if (!attachments.isEmpty()) {
        for (MediaAttachment att : attachments) {
          String url = UserProfileMediaUrls.profileImageUrl(att, user.getId());
          if (url == null || url.isBlank()) {
            continue;
          }
          if (imageUrl == null && att.getMediaKind() == MediaAttachment.MediaKind.IMAGE) {
            imageUrl = url;
          }
          if (imageUrl != null) {
            break;
          }
        }
      }

      return ReviewDto.builder()
          .id(review.getId())
          .propertyId(review.getProperty() != null ? review.getProperty().getId() : null)
          .organizationId(
              review.getOrganization() != null ? review.getOrganization().getId() : null)
          .userId(review.getUser().getId())
          .userName(review.getUser().getFirstName() + " " + review.getUser().getLastName())
          .userImageUrl(imageUrl)
          .rating(review.getRating())
          .comment(review.getComment())
          .createdAt(review.getCreatedAt())
          .build();
    } else {
      return ReviewDto.builder()
          .id(review.getId())
          .propertyId(review.getProperty() != null ? review.getProperty().getId() : null)
          .organizationId(
              review.getOrganization() != null ? review.getOrganization().getId() : null)
          .userName("Anonymous")
          .rating(review.getRating())
          .comment(review.getComment())
          .createdAt(review.getCreatedAt())
          .build();
    }
  }
}
