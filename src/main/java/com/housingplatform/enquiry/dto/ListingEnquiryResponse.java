package com.housingplatform.enquiry.dto;

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
public class ListingEnquiryResponse {
  private UUID id;
  private String subjectType;
  private UUID propertyId;
  private UUID organizationId;
  private String subjectTitle;
  private String organizationName;
  private String name;
  private String email;
  private String phoneNumber;
  private String message;
  private String status;
  private String pageUrl;
  private String locale;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
