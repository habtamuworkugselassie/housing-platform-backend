package com.housingplatform.exhibition.dto;

import com.housingplatform.exhibition.domain.SimulcastTarget;
import java.util.UUID;

/** Admin view of a simulcast destination. The stream key is never exposed — only that one is set. */
public record SimulcastTargetResponse(
    UUID id, String platform, String label, String rtmpUrl, boolean hasKey, boolean enabled) {

  public static SimulcastTargetResponse from(SimulcastTarget t) {
    return new SimulcastTargetResponse(
        t.getId(),
        t.getPlatform() == null ? null : t.getPlatform().name(),
        t.getLabel(),
        t.getRtmpUrl(),
        t.getStreamKey() != null && !t.getStreamKey().isBlank(),
        t.isEnabled());
  }
}
