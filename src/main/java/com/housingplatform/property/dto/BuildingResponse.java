package com.housingplatform.property.dto;

import com.housingplatform.property.domain.Building;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingResponse {
  private UUID id;
  private String name;
  private String description;
  private String address;
  private String city;
  private String state;
  private String country;
  private String zipCode;
  private Double latitude;
  private Double longitude;
  private Integer totalFloors;
  private Integer totalUnits;
  private UUID realEstateCompanyId;
  private String realEstateCompanyName;

  /** True when the real estate company is fully verified. Kept for backward compatibility. */
  private Boolean realEstateCompanyVerified;

  /** Verification level: NONE, HALF, FULL. Used for half vs fully verified badge. */
  private String realEstateCompanyVerificationLevel;

  private UUID agentId;
  private Building.BuildingType buildingType;
  private Building.BuildingStatus status;
  private String amenities;
  private String facilities;
  private Integer yearBuilt;
  private Building.BuildingCategory category;
  private Integer constructionPercentage;
  private Boolean isFullyFurnished;
  private Integer availableUnits;
  private Integer occupiedUnits;
  private Boolean isSponsored;
  private String sponsorshipType;
  private List<PropertyResponse> units;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
