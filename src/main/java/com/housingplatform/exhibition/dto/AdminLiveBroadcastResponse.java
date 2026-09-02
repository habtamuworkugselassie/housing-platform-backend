package com.housingplatform.exhibition.dto;

import com.housingplatform.exhibition.domain.LiveBroadcast;
import java.time.LocalDateTime;
import java.util.UUID;

/** Admin/moderation view of a broadcast (adds email, room, IP, timestamps). */
public record AdminLiveBroadcastResponse(
    UUID id,
    String room,
    String title,
    String broadcasterName,
    String broadcasterEmail,
    String broadcasterRole,
    String companyName,
    String status,
    String hlsUrl,
    String recordingUrl,
    String requesterIp,
    boolean simulcasting,
    boolean recording,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminLiveBroadcastResponse from(LiveBroadcast b) {
    return new AdminLiveBroadcastResponse(
        b.getId(),
        b.getRoom(),
        b.getTitle(),
        b.getBroadcasterName(),
        b.getBroadcasterEmail(),
        b.getBroadcasterRole() == null ? null : b.getBroadcasterRole().name(),
        b.getCompanyName(),
        b.getStatus() == null ? null : b.getStatus().name(),
        b.getHlsUrl(),
        b.getRecordingUrl(),
        b.getRequesterIp(),
        b.getEgressId() != null && !b.getEgressId().isBlank(),
        b.getRecordingEgressId() != null && !b.getRecordingEgressId().isBlank(),
        b.getCreatedAt(),
        b.getUpdatedAt());
  }
}
