package com.housingplatform.identity.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public DTO for an organization with an active sponsorship, used on the landing page carousel and
 * splash.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SponsoredOrganizationResponse {
  private UUID id;
  private String name;
  private String logoUrl;
  private String videoUrl;

  /** First image URL from organization media (for splash/hero when no video). */
  private String splashImageUrl;

  private String address;
  private String city;
  private String country;
  private String sponsorshipType;
  private BigDecimal basePrice;
}
