package com.housingplatform.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Update public landing display timings (milliseconds)")
public record DisplaySettingsUpdateRequest(
    @NotNull
        @Min(3000)
        @Max(300000)
        @Schema(description = "Time between sponsor carousel slides", example = "10000")
        Long sponsorCarouselAutoplayMs,
    @NotNull
        @Min(3000)
        @Max(300000)
        @Schema(description = "Time between images/videos within a sidebar ad", example = "12000")
        Long sidebarMediaRotationMs,
    @NotNull
        @Min(5000)
        @Max(600000)
        @Schema(
            description = "Time before rotating side columns to the next sponsors",
            example = "35000")
        Long sidebarLayoutRotationMs,
    @NotNull
        @Schema(
            description =
                "Show the sponsorship packages section (pricing & benefits) on the exhibition"
                    + " landing page",
            example = "true")
        Boolean exhibitionSponsorshipPackagesVisible,
    @NotNull
        @Schema(
            description =
                "Show base prices on sponsorship package cards and in the details modal on the"
                    + " exhibition landing page",
            example = "true")
        Boolean exhibitionSponsorshipPackagePricesVisible,
    @Schema(description = "Show the live broadcast section on the exhibition page", example = "false")
        Boolean exhibitionLiveVisible,
    @Schema(description = "Live video source: EXTERNAL_EMBED or HLS", example = "EXTERNAL_EMBED")
        String liveSourceType,
    @Schema(description = "External live watch/embed URL (YouTube or Facebook Live)")
        String liveEmbedUrl,
    @Schema(description = "Self-hosted HLS (.m3u8) stream URL") String liveHlsUrl,
    @Schema(description = "Heading shown above the live player") String liveTitle,
    @Schema(description = "Watch-on YouTube link for the live zone") String liveYoutubeUrl,
    @Schema(description = "Watch-on TikTok link for the live zone") String liveTiktokUrl,
    @Schema(description = "Watch-on Facebook link for the live zone") String liveFacebookUrl,
    @Schema(description = "Show the visitor video-feedback section on the exhibition page", example = "false")
        Boolean exhibitionFeedbackVisible,
    @Schema(
            description = "Publish submitted feedback videos immediately (else hold for review)",
            example = "false")
        Boolean exhibitionFeedbackAutoPublish,
        Boolean exhibitionLiveAutoSimulcast) {}
