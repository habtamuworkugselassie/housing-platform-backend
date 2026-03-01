package com.housingplatform.property.service;

import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.PropertyRequest;
import com.housingplatform.property.dto.PropertyResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PropertyMapperImpl implements PropertyMapper {

  @Override
  public Property toEntity(PropertyRequest request) {
    if (request == null) {
      return null;
    }
    return Property.builder()
        .title(request.getTitle())
        .description(request.getDescription())
        .type(request.getType())
        .priceETB(request.getPriceETB())
        .priceUSD(request.getPriceUSD())
        .address(request.getAddress())
        .city(request.getCity())
        .state(request.getState())
        .country(request.getCountry())
        .zipCode(request.getZipCode())
        .latitude(request.getLatitude())
        .longitude(request.getLongitude())
        .bedrooms(request.getBedrooms())
        .bathrooms(request.getBathrooms())
        .area(request.getArea())
        .floorNumber(request.getFloorNumber())
        .totalFloors(request.getTotalFloors())
        .realEstateCompanyId(request.getRealEstateCompanyId())
        .agentId(request.getAgentId())
        .unitNumber(request.getUnitNumber())
        .constructionStatus(request.getConstructionStatus())
        .category(request.getCategory())
        .constructionPercentage(request.getConstructionPercentage())
        .isFullyFurnished(request.getIsFullyFurnished())
        .build();
  }

  @Override
  public PropertyResponse toResponse(Property property) {
    if (property == null) {
      return null;
    }
    UUID buildingId = property.getBuilding() != null ? property.getBuilding().getId() : null;
    String buildingName = property.getBuilding() != null ? property.getBuilding().getName() : null;
    return PropertyResponse.builder()
        .id(property.getId())
        .title(property.getTitle())
        .description(property.getDescription())
        .type(property.getType())
        .status(property.getStatus())
        .verificationStatus(property.getVerificationStatus())
        .priceETB(property.getPriceETB())
        .priceUSD(property.getPriceUSD())
        .address(property.getAddress())
        .city(property.getCity())
        .state(property.getState())
        .country(property.getCountry())
        .zipCode(property.getZipCode())
        .latitude(property.getLatitude())
        .longitude(property.getLongitude())
        .bedrooms(property.getBedrooms())
        .bathrooms(property.getBathrooms())
        .area(property.getArea())
        .floorNumber(property.getFloorNumber())
        .totalFloors(property.getTotalFloors())
        .realEstateCompanyId(property.getRealEstateCompanyId())
        .agentId(property.getAgentId())
        .buildingId(buildingId)
        .buildingName(buildingName)
        .unitNumber(property.getUnitNumber())
        .constructionStatus(property.getConstructionStatus())
        .category(property.getCategory())
        .constructionPercentage(property.getConstructionPercentage())
        .isFullyFurnished(property.getIsFullyFurnished())
        .createdAt(property.getCreatedAt())
        .updatedAt(property.getUpdatedAt())
        .build();
  }

  @Override
  public void updateEntity(Property property, PropertyRequest request) {
    if (request == null) {
      return;
    }
    if (request.getTitle() != null) {
      property.setTitle(request.getTitle());
    }
    if (request.getDescription() != null) {
      property.setDescription(request.getDescription());
    }
    if (request.getType() != null) {
      property.setType(request.getType());
    }
    if (request.getPriceETB() != null) {
      property.setPriceETB(request.getPriceETB());
    }
    if (request.getPriceUSD() != null) {
      property.setPriceUSD(request.getPriceUSD());
    }
    if (request.getAddress() != null) {
      property.setAddress(request.getAddress());
    }
    if (request.getCity() != null) {
      property.setCity(request.getCity());
    }
    if (request.getState() != null) {
      property.setState(request.getState());
    }
    if (request.getCountry() != null) {
      property.setCountry(request.getCountry());
    }
    if (request.getZipCode() != null) {
      property.setZipCode(request.getZipCode());
    }
    if (request.getLatitude() != null) {
      property.setLatitude(request.getLatitude());
    }
    if (request.getLongitude() != null) {
      property.setLongitude(request.getLongitude());
    }
    if (request.getBedrooms() != null) {
      property.setBedrooms(request.getBedrooms());
    }
    if (request.getBathrooms() != null) {
      property.setBathrooms(request.getBathrooms());
    }
    if (request.getArea() != null) {
      property.setArea(request.getArea());
    }
    if (request.getFloorNumber() != null) {
      property.setFloorNumber(request.getFloorNumber());
    }
    if (request.getTotalFloors() != null) {
      property.setTotalFloors(request.getTotalFloors());
    }
    if (request.getRealEstateCompanyId() != null) {
      property.setRealEstateCompanyId(request.getRealEstateCompanyId());
    }
    if (request.getAgentId() != null) {
      property.setAgentId(request.getAgentId());
    }
    if (request.getUnitNumber() != null) {
      property.setUnitNumber(request.getUnitNumber());
    }
    if (request.getConstructionStatus() != null) {
      property.setConstructionStatus(request.getConstructionStatus());
    }
    if (request.getCategory() != null) {
      property.setCategory(request.getCategory());
    }
    if (request.getConstructionPercentage() != null) {
      property.setConstructionPercentage(request.getConstructionPercentage());
    }
    if (request.getIsFullyFurnished() != null) {
      property.setIsFullyFurnished(request.getIsFullyFurnished());
    }
  }
}
