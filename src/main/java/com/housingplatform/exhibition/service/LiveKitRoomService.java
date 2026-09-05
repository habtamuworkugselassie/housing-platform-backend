package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.config.LiveKitProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Thin client for LiveKit's server RoomService (Twirp/JSON), used for the kill-switch. Best-effort:
 * the DB status is authoritative; this forcibly disconnects an in-flight stream.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveKitRoomService {

  private final LiveKitProperties properties;
  private final LiveKitTokenService tokenService;
  private final RestTemplate restTemplate = new RestTemplate();

  /**
   * Ensure a LiveKit room exists. Rooms are created lazily when the first participant joins, but
   * egress (recording / composite simulcast) must attach to an existing room — starting egress
   * before the broadcaster has connected fails with "requested room does not exist". Pre-creating
   * the room here (idempotent) lets egress attach immediately; it captures the feed as soon as the
   * broadcaster publishes. empty_timeout closes the room if nobody ever connects. Best-effort.
   */
  public void createRoom(String room) {
    if (!properties.isConfigured() || room == null || room.isBlank()) {
      return;
    }
    try {
      String adminToken = tokenService.mintServerAdmin(room, true, false, 60);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(adminToken);
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("name", room);
      body.put("empty_timeout", 300); // close a room nobody joins after 5 min
      body.put("departure_timeout", 20); // and shortly after the last participant leaves
      HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
      restTemplate.postForEntity(
          properties.httpUrl() + "/twirp/livekit.RoomService/CreateRoom", req, String.class);
    } catch (Exception e) {
      // Non-fatal: if the broadcaster connects first the room exists anyway; recording is best-effort.
      log.warn("LiveKit CreateRoom failed for room {}: {}", room, e.getMessage());
    }
  }

  /** Force-close a room: disconnects the broadcaster and all viewers immediately. */
  public void deleteRoom(String room) {
    if (!properties.isConfigured()) {
      return;
    }
    try {
      // An admin token scoped to the room authorizes the server API call.
      String adminToken = tokenService.mintServerAdmin(room, true, false, 60);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(adminToken);
      HttpEntity<Map<String, String>> req = new HttpEntity<>(Map.of("room", room), headers);
      restTemplate.postForEntity(
          properties.httpUrl() + "/twirp/livekit.RoomService/DeleteRoom", req, String.class);
    } catch (Exception e) {
      // Non-fatal: the stream is marked ENDED in the DB regardless; log for follow-up.
      log.warn("LiveKit DeleteRoom failed for room {}: {}", room, e.getMessage());
    }
  }
}
