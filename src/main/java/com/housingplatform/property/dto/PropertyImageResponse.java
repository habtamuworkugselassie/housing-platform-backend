package com.housingplatform.property.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyImageResponse {
    private UUID id;
    private String imageUrl;
    private String caption;
    private Integer displayOrder;
    private Boolean isPrimary;
}
