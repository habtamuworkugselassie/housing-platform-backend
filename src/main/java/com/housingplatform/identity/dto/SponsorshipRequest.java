package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Organization;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SponsorshipRequest {
    @NotNull(message = "Sponsorship type is required")
    private Organization.SponsorshipType sponsorshipType;
    
    private LocalDateTime startDate;
    
    private LocalDateTime endDate;
}
