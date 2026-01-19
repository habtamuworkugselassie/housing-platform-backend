package com.housingplatform.banking.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "financing_offers")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class FinancingOffer extends BaseAuditEntity {
    
    @Column(name = "bank_id", nullable = false)
    private UUID bankId;
    
    @Column(name = "credit_product_id", nullable = false)
    private UUID creditProductId;
    
    @Column(name = "property_id")
    private UUID propertyId; // Optional: can be null for general offers
    
    @Column(name = "building_id")
    private UUID buildingId; // Optional: for building-level offers
    
    @Column(name = "project_id")
    private UUID projectId; // Optional: for project-level offers
    
    @Column(name = "special_interest_rate", precision = 5, scale = 2)
    private BigDecimal specialInterestRate; // Override product rate if provided
    
    @Column(name = "special_ltv_ratio", precision = 5, scale = 2)
    private BigDecimal specialLTVRatio; // Override product LTV if provided
    
    @Column(columnDefinition = "TEXT")
    private String specialTerms; // Additional terms specific to this offer
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancingOfferStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String approvalNotes;
    
    public enum FinancingOfferStatus {
        ACTIVE, INACTIVE, EXPIRED, PENDING_APPROVAL
    }
}
