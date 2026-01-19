package com.housingplatform.property.dto;

import com.housingplatform.property.domain.Building;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class BuildingRequest {
    
    @NotBlank(message = "Building name is required")
    private String name;
    
    private String description;
    
    @NotBlank(message = "Address is required")
    private String address;
    
    @NotBlank(message = "City is required")
    private String city;
    
    private String state;
    
    @NotBlank(message = "Country is required")
    private String country;
    
    private String zipCode;
    
    private Double latitude;
    private Double longitude;
    
    @NotNull(message = "Total floors is required")
    @Min(value = 1, message = "Total floors must be at least 1")
    private Integer totalFloors;
    
    @NotNull(message = "Total units is required")
    @Min(value = 1, message = "Total units must be at least 1")
    private Integer totalUnits;
    
    @NotNull(message = "Building type is required")
    private Building.BuildingType buildingType;
    
    @NotNull(message = "Category is required")
    private Building.BuildingCategory category;
    
    private Building.BuildingStatus status;
    
    @Min(value = 0, message = "Construction percentage must be between 0 and 100")
    @Max(value = 100, message = "Construction percentage must be between 0 and 100")
    private Integer constructionPercentage;
    
    private Boolean isFullyFurnished;
    
    private String amenities;
    private String facilities;
    private Integer yearBuilt;
    
    private UUID agentId;
}
