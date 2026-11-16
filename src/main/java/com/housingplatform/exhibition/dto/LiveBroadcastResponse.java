package com.housingplatform.exhibition.dto;

import com.housingplatform.exhibition.domain.LiveBroadcast;
import java.util.UUID;

/**
 * Public view of a live/approved broadcast (no email / IP). {@code manageKey} is only filled in the
 * response to the go-live request itself: it is the broadcaster's proof of ownership for publish /
 * end / co-host moderation, sent back as the {@code X-Broadcast-Key} header.
 */
public record LiveBroadcastResponse(
    UUID id,
    String title,
    String broadcasterName,
    String broadcasterRole,
    String companyName,
    String status,
    String hlsUrl,
    String recordingUrl,
    String manageKey) {

  public static LiveBroadcastResponse from(LiveBroadcast b) {
    return withManageKey(b, null);
  }

  public static LiveBroadcastResponse withManageKey(LiveBroadcast b, String manageKey) {
    return new LiveBroadcastResponse(
        b.getId(),
        b.getTitle(),
        b.getBroadcasterName(),
        b.getBroadcasterRole() == null ? null : b.getBroadcasterRole().name(),
        b.getCompanyName(),
        b.getStatus() == null ? null : b.getStatus().name(),
        b.getHlsUrl(),
        b.getRecordingUrl(),
        manageKey);
  }
}
