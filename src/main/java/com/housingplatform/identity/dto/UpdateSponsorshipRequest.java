package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Sponsorship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
