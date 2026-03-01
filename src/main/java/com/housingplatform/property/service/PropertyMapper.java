package com.housingplatform.property.service;

import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.PropertyImageResponse;
import com.housingplatform.property.dto.PropertyRequest;
import com.housingplatform.property.dto.PropertyResponse;

/**
 * Property entity mapper. Implementation is in PropertyMapperImpl (no MapStruct so IDE runs work).
 */
public interface PropertyMapper {
  Property toEntity(PropertyRequest request);

  PropertyResponse toResponse(Property property);

  default PropertyResponse toResponseWithImages(Property property) {
    PropertyResponse response = toResponse(property);

    // Set building information
    if (property.getBuilding() != null) {
      response.setBuildingId(property.getBuilding().getId());
      response.setBuildingName(property.getBuilding().getName());
    }

    if (property.getImages() != null && !property.getImages().isEmpty()) {
      response.setImages(
          property.getImages().stream()
              .map(
                  img -> {
                    // If file is stored in DB, use endpoint URL; otherwise use external URL
                    String imageUrl =
                        img.hasFileData()
                            ? "/api/v1/properties/"
                                + property.getId()
                                + "/images/"
                                + img.getId()
                                + "/file"
                            : img.getImageUrl();
                    return PropertyImageResponse.builder()
                        .id(img.getId())
                        .imageUrl(imageUrl)
                        .caption(img.getCaption())
                        .displayOrder(img.getDisplayOrder())
                        .isPrimary(img.getIsPrimary())
                        .build();
                  })
              .toList());
    }
    return response;
  }

  void updateEntity(Property property, PropertyRequest request);
}
