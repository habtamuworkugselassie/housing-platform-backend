package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Sponsorship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSponsorshipRequest {
    @NotBlank(message = "Sponsorship name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Sponsorship type is required")
    private Sponsorship.SponsorshipType type;
    
    @NotNull(message = "Base price is required")
    @Positive(message = "Base price must be positive")
    private BigDecimal basePrice;
    
    private String features;
    
    private String notes;
}
