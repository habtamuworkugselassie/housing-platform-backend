package com.housingplatform.property.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyImageRequest {
  private String imageUrl;
  private String caption;
  private Integer displayOrder;
  private Boolean isPrimary;
}
