package com.housingplatform.exhibition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.housingplatform.exhibition.config.LiveKitProperties;
import com.housingplatform.exhibition.dto.IngressResponse;
import com.housingplatform.shared.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Creates/deletes LiveKit Ingress endpoints so an organizer's professional camera (via OBS or a
 * hardware encoder) can publish into a room over RTMP or WHIP. Wraps the LiveKit IngressService
 * Twirp/JSON API; the API secret never leaves the server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveKitIngressService {

  // LiveKit IngressInput enum: RTMP_INPUT=0, WHIP_INPUT=1, URL_INPUT=2
  private static final int RTMP_INPUT = 0;
  private static final int WHIP_INPUT = 1;

  private final LiveKitProperties properties;
  private final LiveKitTokenService tokenService;
  private final RestTemplate restTemplate = new RestTemplate();

  /**
   * @param type "RTMP" (default) or "WHIP"
   */
  public IngressResponse createIngress(String room, String name, String type) {
    if (!properties.isConfigured()) {
      throw new BusinessException("Live streaming is not configured.");
    }
    boolean whip = "WHIP".equalsIgnoreCase(type);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("input_type", whip ? WHIP_INPUT : RTMP_INPUT);
    body.put("name", name);
    body.put("room_name", room);
    body.put("participant_identity", "ingress-" + room);
    body.put("participant_name", name);

    JsonNode res = post("CreateIngress", body);
    return new IngressResponse(
        text(res, "ingress_id"),
        whip ? "WHIP" : "RTMP",
        text(res, "url"),
        text(res, "stream_key"));
  }

  public void deleteIngress(String ingressId) {
    if (ingressId == null || ingressId.isBlank() || !properties.isConfigured()) {
      return;
    }
    try {
      post("DeleteIngress", Map.of("ingress_id", ingressId));
    } catch (Exception e) {
      log.warn("LiveKit DeleteIngress failed for {}: {}", ingressId, e.getMessage());
    }
  }

  private JsonNode post(String method, Map<String, ?> body) {
    String token = tokenService.mintServerAdmin(null, false, true, 60);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    HttpEntity<Map<String, ?>> req = new HttpEntity<>(body, headers);
    return restTemplate.postForObject(
        properties.httpUrl() + "/twirp/livekit.Ingress/" + method, req, JsonNode.class);
  }

  private static String text(JsonNode node, String field) {
    return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
  }
}
