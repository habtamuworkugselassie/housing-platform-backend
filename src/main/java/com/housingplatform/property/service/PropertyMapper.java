package com.housingplatform.property.service;

import com.housingplatform.property.domain.Property;
import com.housingplatform.property.domain.PropertyImage;
import com.housingplatform.property.dto.PropertyImageResponse;
import com.housingplatform.property.dto.PropertyRequest;
import com.housingplatform.property.dto.PropertyResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PropertyMapper {
    Property toEntity(PropertyRequest request);
    
    @org.mapstruct.Mapping(target = "images", ignore = true)
    @org.mapstruct.Mapping(target = "buildingId", source = "building.id")
    @org.mapstruct.Mapping(target = "buildingName", source = "building.name")
    PropertyResponse toResponse(Property property);
    
    default PropertyResponse toResponseWithImages(Property property) {
        PropertyResponse response = toResponse(property);
        
        // Set building information
        if (property.getBuilding() != null) {
            response.setBuildingId(property.getBuilding().getId());
            response.setBuildingName(property.getBuilding().getName());
        }
        
        if (property.getImages() != null && !property.getImages().isEmpty()) {
            response.setImages(property.getImages().stream()
                    .map(img -> {
                        // If file is stored in DB, use endpoint URL; otherwise use external URL
                        String imageUrl = img.hasFileData() 
                                ? "/api/v1/properties/" + property.getId() + "/images/" + img.getId() + "/file"
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
    
    void updateEntity(@MappingTarget Property property, PropertyRequest request);
}
