package com.housingplatform.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAssignSponsorshipRequest {

  @NotNull(message = "Organization ID is required")
  private UUID organizationId;

  @NotNull(message = "Sponsorship ID is required")
  private UUID sponsorshipId;

  /** Start date (date-only, e.g. "2026-03-01"). Interpreted as start of day. */
  @NotNull(message = "Start date is required")
  private LocalDate startDate;

  /** End date (date-only, e.g. "2026-12-31"). Interpreted as end of day. */
  @NotNull(message = "End date is required")
  private LocalDate endDate;

  private String notes;
  private java.math.BigDecimal amount;
  private String paymentReference;

  /** If true, the application is created and immediately approved. */
  private Boolean autoApprove;
}
