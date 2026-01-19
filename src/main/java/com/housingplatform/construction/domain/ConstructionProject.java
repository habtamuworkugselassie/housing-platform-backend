package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "construction_projects")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ConstructionProject extends BaseAuditEntity {
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "property_id")
    private UUID propertyId; // Optional: for property-specific projects
    
    @Column(name = "building_id")
    private UUID buildingId; // Optional: for building-specific projects
    
    @Column(name = "real_estate_company_id", nullable = false)
    private UUID realEstateCompanyId;
    
    @Column(name = "project_manager_id")
    private UUID projectManagerId; // User ID of project manager
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectType type;
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;
    
    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal budget; // Total budget
    
    @Column(name = "total_cost", precision = 19, scale = 2)
    private BigDecimal totalCost; // Actual total cost
    
    @Column(name = "currency", nullable = false)
    @Enumerated(EnumType.STRING)
    private com.housingplatform.shared.domain.Currency currency;
    
    @Column(name = "location_address")
    private String locationAddress;
    
    @Column(name = "location_city")
    private String locationCity;
    
    @Column(name = "location_state")
    private String locationState;
    
    @Column(name = "location_country")
    private String locationCountry;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ConstructionPhase> phases = new ArrayList<>();
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaterialOrder> orders = new ArrayList<>();
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BillOfQuantities> billsOfQuantities = new ArrayList<>();
    
    public enum ProjectStatus {
        PLANNING, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED
    }
    
    public enum ProjectType {
        NEW_CONSTRUCTION, RENOVATION, EXPANSION, REPAIR, MAINTENANCE
    }
}
