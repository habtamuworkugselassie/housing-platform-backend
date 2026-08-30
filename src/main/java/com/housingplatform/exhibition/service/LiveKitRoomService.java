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
