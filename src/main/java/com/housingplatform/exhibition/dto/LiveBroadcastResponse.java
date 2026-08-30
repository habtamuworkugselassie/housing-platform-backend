package com.housingplatform.exhibition.dto;

import com.housingplatform.exhibition.domain.LiveBroadcast;
import java.util.UUID;

/** Public view of a live/approved broadcast (no email / IP). */
public record LiveBroadcastResponse(
    UUID id,
    String title,
    String broadcasterName,
    String broadcasterRole,
    String companyName,
    String status,
    String hlsUrl) {

  public static LiveBroadcastResponse from(LiveBroadcast b) {
    return new LiveBroadcastResponse(
        b.getId(),
        b.getTitle(),
        b.getBroadcasterName(),
        b.getBroadcasterRole() == null ? null : b.getBroadcasterRole().name(),
        b.getCompanyName(),
        b.getStatus() == null ? null : b.getStatus().name(),
        b.getHlsUrl());
  }
}
