package com.housingplatform.property.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDto {
  private UUID id;
  private UUID propertyId;
  private UUID userId;
  private String userName;
  private String userImageUrl;
  private Integer rating;
  private String comment;
  private LocalDateTime createdAt;
}
