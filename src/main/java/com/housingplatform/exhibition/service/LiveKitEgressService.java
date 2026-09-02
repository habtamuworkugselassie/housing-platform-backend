package com.housingplatform.exhibition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.housingplatform.exhibition.config.LiveKitProperties;
import com.housingplatform.shared.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Starts/stops a LiveKit <b>RoomComposite egress</b> that re-streams a live room out to one or more
 * external RTMP(S) destinations (YouTube Live, Facebook Live, TikTok, …) in real time — the
 * "simulcast" feature. Wraps the LiveKit EgressService Twirp/JSON API; RTMP stream keys never leave
 * the server (they are pushed straight to LiveKit and never returned to any client).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveKitEgressService {

  // LiveKit StreamProtocol enum: DEFAULT_PROTOCOL=0, RTMP=1, SRT=2
  private static final int PROTOCOL_RTMP = 1;
  // LiveKit EncodedFileType enum: DEFAULT_FILETYPE=0, MP4=1, OGG=2
  private static final int FILETYPE_MP4 = 1;

  private final LiveKitProperties properties;
  private final LiveKitTokenService tokenService;
  private final RestTemplate restTemplate = new RestTemplate();

  /**
   * Start a composite egress of {@code room} pushing to every RTMP URL in {@code rtmpUrls}.
   *
   * @return the egress id (store it so the stream can be stopped later)
   */
  public String startRtmp(String room, List<String> rtmpUrls) {
    if (!properties.isConfigured()) {
      throw new BusinessException("Live streaming is not configured.");
    }
    if (rtmpUrls == null || rtmpUrls.isEmpty()) {
      throw new BusinessException("No simulcast destinations selected.");
    }

    Map<String, Object> streamOutput = new LinkedHashMap<>();
    streamOutput.put("protocol", PROTOCOL_RTMP);
    streamOutput.put("urls", rtmpUrls);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("room_name", room);
    body.put("layout", "grid");
    body.put("stream_outputs", List.of(streamOutput));

    JsonNode res = post("StartRoomCompositeEgress", body);
    String egressId = text(res, "egress_id");
    if (egressId == null || egressId.isBlank()) {
      throw new BusinessException("Could not start the social simulcast. Please try again.");
    }
    return egressId;
  }

  /**
   * Start a RoomComposite <b>file</b> egress that records {@code room} to a single MP4 written into
   * the egress container's {@code livekit.recordingDir}. Returns the relative filename (used to
   * build the public URL) plus the egress id, or {@code null} if recording is disabled/unconfigured.
   *
   * @return a {@link Recording} handle, or {@code null} when recording is off
   */
  public Recording startFileRecording(String room) {
    if (!properties.isConfigured() || !properties.isRecordingEnabled()) {
      return null;
    }
    // Deterministic, collision-free name; kept flat so the frontend can serve it directly.
    String filename = room + "-" + java.time.Instant.now().toEpochMilli() + ".mp4";
    String filepath = properties.getRecordingDir().replaceAll("/+$", "") + "/" + filename;

    Map<String, Object> fileOutput = new LinkedHashMap<>();
    fileOutput.put("file_type", FILETYPE_MP4);
    fileOutput.put("filepath", filepath);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("room_name", room);
    body.put("layout", "grid"); // captures every publisher (broadcaster + co-hosts)
    body.put("file_outputs", List.of(fileOutput));

    try {
      JsonNode res = post("StartRoomCompositeEgress", body);
      String egressId = text(res, "egress_id");
      if (egressId == null || egressId.isBlank()) {
        log.warn("StartRoomCompositeEgress (file) for room {} returned no egress_id", room);
        return null;
      }
      return new Recording(egressId, filename);
    } catch (Exception e) {
      // Recording is best-effort; never block go-live because egress is down.
      log.warn("Could not start file recording for room {}: {}", room, e.getMessage());
      return null;
    }
  }

  /** Handle for a started file recording: the egress id to stop it, and the output filename. */
  public record Recording(String egressId, String filename) {}

  /** Stop a running egress. Best-effort: never throws (used on cleanup / kill-switch paths). */
  public void stop(String egressId) {
    if (egressId == null || egressId.isBlank() || !properties.isConfigured()) {
      return;
    }
    try {
      post("StopEgress", Map.of("egress_id", egressId));
    } catch (Exception e) {
      log.warn("LiveKit StopEgress failed for {}: {}", egressId, e.getMessage());
    }
  }

  private JsonNode post(String method, Map<String, ?> body) {
    String token = tokenService.mintEgressAdmin(60);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    HttpEntity<Map<String, ?>> req = new HttpEntity<>(body, headers);
    return restTemplate.postForObject(
        properties.httpUrl() + "/twirp/livekit.Egress/" + method, req, JsonNode.class);
  }

  private static String text(JsonNode node, String field) {
    return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
  }
}
