package com.housingplatform.property.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * First image and/or video URL from an organization's properties. Used for sponsor carousel
 * fallback.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirstPropertyMediaResponse {
  private String imageUrl;
  private String videoUrl;
}
