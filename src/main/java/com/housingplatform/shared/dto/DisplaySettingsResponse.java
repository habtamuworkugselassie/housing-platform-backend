package com.housingplatform.shared.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DisplaySettingsResponse {
  long sponsorCarouselAutoplayMs;
  long sidebarMediaRotationMs;
  long sidebarLayoutRotationMs;

  /** Contact block for the site footer; from the base organization when configured and found. */
  FooterContactResponse footer;
}
