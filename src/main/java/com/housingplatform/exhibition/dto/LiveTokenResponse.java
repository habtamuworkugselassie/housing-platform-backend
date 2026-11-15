package com.housingplatform.exhibition.dto;

/** Everything a client needs to connect to LiveKit: the server URL, a scoped token, the room, and
 * (for viewers) an HLS fallback URL when present. */
public record LiveTokenResponse(String url, String token, String room, String hlsUrl) {}
