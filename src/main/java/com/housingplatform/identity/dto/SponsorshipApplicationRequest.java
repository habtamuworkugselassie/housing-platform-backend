package com.housingplatform.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SponsorshipApplicationRequest {
  @NotNull(message = "Sponsorship ID is required")
  private java.util.UUID sponsorshipId;

  @NotNull(message = "Start date is required")
  private LocalDateTime startDate;

  @NotNull(message = "End date is required")
  private LocalDateTime endDate;

  private String notes;

  private java.math.BigDecimal amount;

  private String paymentReference;
}
