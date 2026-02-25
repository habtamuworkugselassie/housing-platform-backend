package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.SponsorshipApplication;
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
public class SponsorshipApplicationResponse {
  private UUID id;
  private UUID sponsorshipId;
  private String sponsorshipName;
  private SponsorshipResponse sponsorship;
  private UUID organizationId;
  private String organizationName;
  private SponsorshipApplication.ApplicationStatus status;
  private LocalDateTime startDate;
  private LocalDateTime endDate;
  private Boolean isActive;
  private String notes;
  private String rejectionReason;
  private java.math.BigDecimal amount;
  private String paymentReference;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
