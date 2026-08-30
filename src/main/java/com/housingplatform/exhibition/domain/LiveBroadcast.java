package com.housingplatform.exhibition.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A gated live-broadcast session. A visitor / exhibitor / organizer requests to go live; an
 * organizer approves before any publish token is issued, and can end (cut) it at any time.
 */
@Entity
@Table(name = "live_broadcast")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LiveBroadcast extends BaseEntity {

  /** LiveKit room name (unique). */
  @Column(nullable = false, unique = true, length = 100)
  private String room;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(name = "broadcaster_name", nullable = false, length = 120)
  private String broadcasterName;

  @Column(name = "broadcaster_email", length = 255)
  private String broadcasterEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "broadcaster_role", nullable = false, length = 20)
  private BroadcasterRole broadcasterRole;

  @Column(name = "company_name", length = 255)
  private String companyName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BroadcastStatus status;

  /** Populated once egress publishes an HLS rendition of the room. */
  @Column(name = "hls_url", length = 512)
  private String hlsUrl;

  /** LiveKit Ingress id when this broadcast is fed by an external encoder (pro camera via OBS). */
  @Column(name = "ingress_id", length = 64)
  private String ingressId;

  @Column(name = "requester_ip", length = 64)
  private String requesterIp;

  public enum BroadcasterRole {
    VISITOR,
    EXHIBITOR,
    ORGANIZER
  }

  public enum BroadcastStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    LIVE,
    ENDED
  }
}
