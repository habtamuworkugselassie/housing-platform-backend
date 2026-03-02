package com.housingplatform.property.dto;

import com.housingplatform.property.domain.Property;
import java.math.BigDecimal;
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
public class PropertyResponse {
  private UUID id;
  private String title;
  private String description;
  private Property.PropertyType type;
  private Property.PropertyStatus status;
  private Property.VerificationStatus verificationStatus;
  private BigDecimal priceETB; // Price in Ethiopian Birr
  private BigDecimal priceUSD; // Price in US Dollars
  private String address;
  private String city;
  private String state;
  private String country;
  private String zipCode;
  private Double latitude;
  private Double longitude;
  private Integer bedrooms;
  private Integer bathrooms;
  private Double area;
  private Integer floorNumber;
  private Integer totalFloors;
  private UUID realEstateCompanyId;
  private String realEstateCompanyName;
  /** Primary contact phone for the real estate (e.g. +251 91 123 4567). */
  private String realEstateCompanyPhone;
  private UUID agentId;
  private UUID buildingId;
  private String buildingName;
  private String unitNumber;
  private Property.ConstructionStatus constructionStatus;
  private Property.PropertyCategory category;
  private Integer constructionPercentage;
  private Boolean isFullyFurnished;
  private Boolean isSponsored;
  private String sponsorshipType;
  private List<PropertyImageResponse> images;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
