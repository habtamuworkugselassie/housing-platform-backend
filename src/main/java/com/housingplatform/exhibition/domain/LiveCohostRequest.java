package com.housingplatform.exhibition.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A viewer's request to co-host (publish their own camera/mic into) a LIVE broadcast. The
 * broadcaster approves or denies it; on approval the viewer is issued a publish token scoped to the
 * broadcast's room, so several people can appear in the same live stream.
 */
@Entity
@Table(name = "live_cohost_request")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LiveCohostRequest extends BaseEntity {

  /** The broadcast (room) this viewer wants to join as a co-host. */
  @Column(name = "broadcast_id", nullable = false)
  private UUID broadcastId;

  /** Display name shown to the broadcaster and next to the co-host's tile. */
  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  /** Stable LiveKit participant identity minted for this co-host (unique within the room). */
  @Column(name = "participant_identity", nullable = false, length = 64)
  private String participantIdentity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CohostStatus status;

  /** Signed-in account behind the request, when the viewer is authenticated (may be null). */
  @Column(name = "requester_user_id")
  private UUID requesterUserId;

  public enum CohostStatus {
    PENDING,
    APPROVED,
    DENIED
  }
}
