package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.config.LiveKitProperties;
import com.housingplatform.shared.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Mints LiveKit access tokens. A LiveKit token is a standard JWT signed HS256 with the LiveKit API
 * secret, carrying a {@code video} grant claim that determines what the holder may do. This is the
 * gate: a broadcaster only ever receives a token with {@code canPublish=true} after an organizer
 * has approved their go-live request.
 */
@Service
@RequiredArgsConstructor
public class LiveKitTokenService {

  private final LiveKitProperties properties;

  private SecretKey signingKey() {
    if (!properties.isConfigured()) {
      throw new BusinessException("Live streaming is not configured.");
    }
    return Keys.hmacShaKeyFor(properties.getApiSecret().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @param identity stable participant identity (unique within the room)
   * @param name display name
   * @param room room name
   * @param canPublish may send audio/video (broadcaster) vs subscribe-only (viewer)
   * @param roomAdmin server-side moderation rights (used for the kill-switch admin token)
   * @param ttlSeconds token lifetime
   */
  public String mint(
      String identity,
      String name,
      String room,
      boolean canPublish,
      boolean roomAdmin,
      long ttlSeconds) {
    Instant now = Instant.now();
    Instant exp = now.plus(ttlSeconds, ChronoUnit.SECONDS);

    Map<String, Object> video = new LinkedHashMap<>();
    video.put("room", room);
    video.put("roomJoin", true);
    video.put("canPublish", canPublish);
    video.put("canSubscribe", true);
    video.put("canPublishData", true);
    if (roomAdmin) {
      video.put("roomAdmin", roomAdmin);
    }

    return Jwts.builder()
        .issuer(properties.getApiKey())
        .subject(identity)
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .notBefore(Date.from(now))
        .expiration(Date.from(exp))
        .claim("name", name == null ? identity : name)
        .claim("video", video)
        .signWith(signingKey())
        .compact();
  }

  /** Server-API token for the RoomService / IngressService Twirp calls (kill-switch, ingress). */
  public String mintServerAdmin(String room, boolean roomAdmin, boolean ingressAdmin, long ttlSeconds) {
    Instant now = Instant.now();
    Instant exp = now.plus(ttlSeconds, ChronoUnit.SECONDS);
    Map<String, Object> video = new LinkedHashMap<>();
    if (room != null && !room.isBlank()) {
      video.put("room", room);
    }
    if (roomAdmin) {
      video.put("roomAdmin", true);
      // Server-admin tokens also create rooms (pre-creating a room so egress can attach before
      // the broadcaster has joined — LiveKit creates rooms lazily otherwise).
      video.put("roomCreate", true);
    }
    if (ingressAdmin) {
      video.put("ingressAdmin", true);
    }
    return Jwts.builder()
        .issuer(properties.getApiKey())
        .subject("server")
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .notBefore(Date.from(now))
        .expiration(Date.from(exp))
        .claim("video", video)
        .signWith(signingKey())
        .compact();
  }

  /** Server-API token for the EgressService Twirp calls (start/stop RTMP simulcast, recording). */
  public String mintEgressAdmin(long ttlSeconds) {
    Instant now = Instant.now();
    Instant exp = now.plus(ttlSeconds, ChronoUnit.SECONDS);
    Map<String, Object> video = new LinkedHashMap<>();
    video.put("roomRecord", true);
    return Jwts.builder()
        .issuer(properties.getApiKey())
        .subject("server")
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .notBefore(Date.from(now))
        .expiration(Date.from(exp))
        .claim("video", video)
        .signWith(signingKey())
        .compact();
  }
}
