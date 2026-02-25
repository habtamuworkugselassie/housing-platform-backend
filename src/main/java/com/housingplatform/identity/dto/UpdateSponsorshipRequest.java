package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Sponsorship;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSponsorshipRequest {
  private String name;
  private String description;
  private Sponsorship.SponsorshipType type;
  private BigDecimal basePrice;
  private String features;
  private Sponsorship.SponsorshipStatus status;
  private String notes;
}
