package com.housingplatform.exhibition.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A reusable social RTMP destination an organizer can push a live broadcast to (YouTube Live,
 * Facebook Live, TikTok, or a custom RTMP endpoint). The stream key is a secret and is never
 * returned to any client — only whether one is set.
 */
@Entity
@Table(name = "simulcast_target")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SimulcastTarget extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Platform platform;

  @Column(nullable = false, length = 120)
  private String label;

  /** RTMP(S) ingest URL, e.g. rtmp://a.rtmp.youtube.com/live2 (without the stream key). */
  @Column(name = "rtmp_url", nullable = false, length = 512)
  private String rtmpUrl;

  /** Secret stream key. Server-only; never serialized to clients. */
  @Column(name = "stream_key", nullable = false, length = 512)
  private String streamKey;

  @Column(nullable = false)
  private boolean enabled;

  /** Full RTMP URL LiveKit egress publishes to: {rtmpUrl}/{streamKey}. */
  public String fullUrl() {
    String base = rtmpUrl == null ? "" : rtmpUrl.trim().replaceAll("/+$", "");
    String key = streamKey == null ? "" : streamKey.trim();
    return base + "/" + key;
  }

  public enum Platform {
    YOUTUBE,
    FACEBOOK,
    TIKTOK,
    INSTAGRAM,
    CUSTOM
  }
}
