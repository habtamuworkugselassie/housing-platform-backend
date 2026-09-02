package com.housingplatform.exhibition.dto;

import com.housingplatform.exhibition.domain.LiveCohostRequest;
import java.util.UUID;

/** A co-host request as seen by the viewer (polling for approval) and the broadcaster (moderating). */
public record CohostRequestResponse(
    UUID id, String displayName, String participantIdentity, String status) {

  public static CohostRequestResponse from(LiveCohostRequest r) {
    return new CohostRequestResponse(
        r.getId(),
        r.getDisplayName(),
        r.getParticipantIdentity(),
        r.getStatus() == null ? null : r.getStatus().name());
  }
}
