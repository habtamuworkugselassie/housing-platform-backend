package com.housingplatform.exhibition.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Create/update a simulcast destination. On update, a blank {@code streamKey} keeps the existing
 * key (so the secret never has to round-trip to the client).
 */
public record SimulcastTargetRequest(
    @NotBlank String platform,
    @NotBlank String label,
    @NotBlank String rtmpUrl,
    String streamKey,
    Boolean enabled) {}
