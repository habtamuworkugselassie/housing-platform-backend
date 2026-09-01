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

  /** When true, the exhibition landing shows the "Live now" broadcast section. */
  @JsonProperty("exhibitionLiveVisible")
  boolean exhibitionLiveVisible;

  /** Live video source: "EXTERNAL_EMBED" (YouTube/Facebook) or "HLS" (self-hosted). */
  String liveSourceType;

  /** External live watch/embed URL (YouTube or Facebook Live). */
  String liveEmbedUrl;

  /** Self-hosted HLS (.m3u8) stream URL. */
  String liveHlsUrl;

  /** Heading shown above the live player. */
  String liveTitle;

  /** "Watch on" social links for the live zone. */
  String liveYoutubeUrl;

  String liveTiktokUrl;

  String liveFacebookUrl;

  /** When true, the exhibition landing shows the visitor video-feedback section. */
  @JsonProperty("exhibitionFeedbackVisible")
  boolean exhibitionFeedbackVisible;

  /** When true, submitted feedback videos are published immediately; when false they await review. */
  @JsonProperty("exhibitionFeedbackAutoPublish")
  boolean exhibitionFeedbackAutoPublish;

  /** When on, an admin/organizer stream auto-simulcasts to all enabled RTMP destinations. */
  @JsonProperty("exhibitionLiveAutoSimulcast")
  boolean exhibitionLiveAutoSimulcast;
}
