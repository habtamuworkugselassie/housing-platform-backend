package com.housingplatform.property.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "buildings")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Building extends BaseAuditEntity {
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private String address;
    
    @Column(nullable = false)
    private String city;
    
    private String state;
    
    @Column(nullable = false)
    private String country;
    
    private String zipCode;
    
    private Double latitude;
    private Double longitude;
    
    @Column(nullable = false)
    private Integer totalFloors;
    
    @Column(nullable = false)
    private Integer totalUnits;
    
    @Column(name = "real_estate_company_id", nullable = false)
    private UUID realEstateCompanyId;
    
    @Column(name = "agent_id")
    private UUID agentId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BuildingType buildingType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BuildingStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String amenities; // JSON or comma-separated list
    
    @Column(columnDefinition = "TEXT")
    private String facilities; // Parking, Elevator, Security, etc.
    
    @Column(name = "year_built")
    private Integer yearBuilt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BuildingCategory category;
    
    @Column(name = "construction_percentage")
    private Integer constructionPercentage; // 0-100, null if not applicable
    
    @Column(name = "is_fully_furnished", nullable = false)
    @Builder.Default
    private Boolean isFullyFurnished = false;
    
    @OneToMany(mappedBy = "building", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Property> units = new ArrayList<>();
    
    public enum BuildingType {
        APARTMENT_COMPLEX, CONDOMINIUM, RESIDENTIAL_COMPLEX, MIXED_USE, COMMERCIAL_RESIDENTIAL
    }
    
    public enum BuildingStatus {
        PLANNED, UNDER_CONSTRUCTION, COMPLETED, RENOVATION
    }
    
    public enum BuildingCategory {
        FOR_SALE, FOR_RENTAL
    }
}
