package com.housingplatform.exhibition.dto;

/**
 * Connection details for an external encoder (organizer professional camera via OBS).
 * For RTMP: point the encoder's server at {@code url} and use {@code streamKey}. For WHIP: publish
 * to {@code url}.
 */
public record IngressResponse(String ingressId, String inputType, String url, String streamKey) {}
