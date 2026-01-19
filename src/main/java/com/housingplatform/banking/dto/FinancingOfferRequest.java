package com.housingplatform.banking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class FinancingOfferRequest {
    
    @NotNull(message = "Credit product ID is required")
    private UUID creditProductId;
    
    private UUID propertyId;
    private UUID buildingId;
    private UUID projectId;
    
    private BigDecimal specialInterestRate;
    private BigDecimal specialLTVRatio;
    private String specialTerms;
}
