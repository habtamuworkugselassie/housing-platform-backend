package com.housingplatform.property.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Property extends BaseAuditEntity {
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus;
    
    @Column(name = "price_etb", precision = 19, scale = 2)
    private BigDecimal priceETB; // Price in Ethiopian Birr
    
    @Column(name = "price_usd", precision = 19, scale = 2)
    private BigDecimal priceUSD; // Price in US Dollars
    
    // At least one price must be provided (enforced at service level)
    
    private String address;
    private String city;
    private String state;
    private String country;
    private String zipCode;
    
    private Double latitude;
    private Double longitude;
    
    private Integer bedrooms;
    private Integer bathrooms;
    private Double area; // in square meters
    private Integer floorNumber;
    private Integer totalFloors;
    
    @Column(name = "real_estate_company_id")
    private UUID realEstateCompanyId;
    
    @Column(name = "agent_id")
    private UUID agentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;
    
    @Column(name = "unit_number")
    private String unitNumber; // e.g., "A-101", "Unit 5B"
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConstructionStatus constructionStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyCategory category;
    
    @Column(name = "construction_percentage")
    private Integer constructionPercentage; // 0-100, null if not applicable
    
    @Column(name = "is_fully_furnished", nullable = false)
    @Builder.Default
    private Boolean isFullyFurnished = false;
    
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PropertyDocument> documents = new ArrayList<>();
    
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PropertyImage> images = new ArrayList<>();
    
    public enum PropertyType {
        APARTMENT, HOUSE, VILLA, CONDOMINIUM, TOWNHOUSE, LAND
    }
    
    public enum PropertyStatus {
        AVAILABLE, SOLD, RESERVED, WITHDRAWN
    }
    
    public enum VerificationStatus {
        PENDING, VERIFIED, REJECTED
    }
    
    public enum ConstructionStatus {
        READY_TO_MOVE, UNDER_CONSTRUCTION, PLANNED
    }
    
    public enum PropertyCategory {
        FOR_SALE, FOR_RENTAL
    }
}
