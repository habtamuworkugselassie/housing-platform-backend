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
        Long sidebarLayoutRotationMs) {}
