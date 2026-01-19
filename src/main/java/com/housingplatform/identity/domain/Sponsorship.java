package com.housingplatform.identity.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sponsorships")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Sponsorship extends BaseAuditEntity {
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SponsorshipType type;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal basePrice;
    
    @Column(columnDefinition = "TEXT")
    private String features;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SponsorshipStatus status = SponsorshipStatus.ACTIVE;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    public enum SponsorshipType {
        BASIC, PREMIER
    }
    
    public enum SponsorshipStatus {
        ACTIVE, INACTIVE
    }
}
