package com.housingplatform.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

  /** When false, the exhibition landing page hides the public sponsorship packages block. */
  @JsonProperty("exhibitionSponsorshipPackagesVisible")
  boolean exhibitionSponsorshipPackagesVisible;

  /** When false, package cards and details modal hide listed prices (section can still show). */
  @JsonProperty("exhibitionSponsorshipPackagePricesVisible")
  boolean exhibitionSponsorshipPackagePricesVisible;
}
