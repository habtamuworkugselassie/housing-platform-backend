package com.housingplatform.property.dto;

import com.housingplatform.property.domain.Property;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PropertyRequest {
    
    @NotBlank(message = "Title is required")
    private String title;
    
    private String description;
    
    @NotNull(message = "Property type is required")
    private Property.PropertyType type;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Price ETB must be greater than 0")
    private BigDecimal priceETB; // Price in Ethiopian Birr
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Price USD must be greater than 0")
    private BigDecimal priceUSD; // Price in US Dollars
    
    // At least one price must be provided (validated at service level)
    
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
    
    @Min(value = 0, message = "Bedrooms must be non-negative")
    private Integer bedrooms;
    
    @Min(value = 0, message = "Bathrooms must be non-negative")
    private Integer bathrooms;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Area must be greater than 0")
    private Double area;
    
    private Integer floorNumber;
    private Integer totalFloors;
    
    // Optional for updates, validated at service level for creates
    private UUID realEstateCompanyId;
    
    private UUID agentId; // Optional: will be set from current user's agent profile
    
    private UUID buildingId; // Optional: if property is part of a building
    private String unitNumber; // Optional: unit number within building (e.g., "A-101", "Unit 5B")
    
    // Optional for updates, validated at service level for creates
    private Property.ConstructionStatus constructionStatus;
    
    // Optional for updates, validated at service level for creates
    private Property.PropertyCategory category;
    
    @Min(value = 0, message = "Construction percentage must be between 0 and 100")
    @Max(value = 100, message = "Construction percentage must be between 0 and 100")
    private Integer constructionPercentage;
    
    private Boolean isFullyFurnished;

    // how much finished in percent
    // is full finished
    // is for rental
    // have loan ready while sells
}
