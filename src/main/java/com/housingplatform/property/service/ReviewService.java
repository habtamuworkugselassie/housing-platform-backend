package com.housingplatform.property.service;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.media.domain.MediaAttachment;
import com.housingplatform.media.repository.MediaAttachmentRepository;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.domain.Review;
import com.housingplatform.property.dto.ReviewDto;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.property.repository.ReviewRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final PropertyRepository propertyRepository;
  private final UserRepository userRepository;
  private final MediaAttachmentRepository mediaAttachmentRepository;

  @Transactional(readOnly = true)
  public List<ReviewDto> getReviewsByPropertyId(UUID propertyId) {
    return reviewRepository.findByPropertyId(propertyId).stream()
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  @Transactional
  public ReviewDto createReview(UUID propertyId, ReviewDto reviewDto) {

    User user = userRepository.findById(reviewDto.getUserId())
            .orElseThrow(() -> new RuntimeException("User not found"));

    Property property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new RuntimeException("Property not found"));

    Review.ReviewBuilder<?, ?> builder = Review.builder()
            .property(property)
            .user(user)
            .rating(reviewDto.getRating())
            .comment(reviewDto.getComment());

    if (reviewDto.getCreatedAt() != null) {
      builder.createdAt(reviewDto.getCreatedAt());
    }

    Review review = builder.build();

    Review savedReview = reviewRepository.save(review);
    return toDto(savedReview);
  }

  private ReviewDto toDto(Review review) {

    User user = review.getUser();
    if (user != null) {
      List<MediaAttachment> attachments =
              mediaAttachmentRepository.findByUserIdOrderByDisplayOrderAsc(user.getId());
      String imageUrl = null;

      if (!attachments.isEmpty()) {
        for (MediaAttachment att : attachments) {
          String url =
                  att.hasFileData()
                          ? "/api/v1/properties/" + user.getId() + "/images/" + att.getId() + "/file"
                        :att.getImageUrl();
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
              .propertyId(review.getProperty().getId())
              .userId(review.getUser().getId())
              .userName(review.getUser().getFirstName() + " " + review.getUser().getLastName())
              .userImageUrl(imageUrl)
              .rating(review.getRating())
              .comment(review.getComment())
              .createdAt(review.getCreatedAt())
              .build();
    } else {
      return null;
    }
  }
}
