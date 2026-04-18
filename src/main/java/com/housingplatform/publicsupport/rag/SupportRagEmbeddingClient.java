package com.housingplatform.publicsupport.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** OpenAI-compatible embeddings API (same base URL / key as support chat LLM). */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupportRagEmbeddingClient {

  private final ObjectMapper objectMapper;

  @Value("${app.support-chat.api-base-url:https://api.openai.com/v1}")
  private String apiBaseUrl;

  @Value("${app.support-chat.api-key:}")
  private String apiKey;

  @Value("${app.support-chat.rag-embedding-model:text-embedding-3-small}")
  private String embeddingModel;

  @Value("${app.support-chat.rag-embedding-dimensions:1536}")
  private int embeddingDimensions;

  public float[] embed(String input) {
    if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(input)) {
      return new float[0];
    }
    String base = apiBaseUrl.trim().replaceAll("/+$", "");
    RestClient client =
        RestClient.builder()
            .baseUrl(base)
            .defaultHeader("Authorization", "Bearer " + apiKey.trim())
            .build();
    Map<String, Object> body = new HashMap<>();
    body.put("model", embeddingModel);
    body.put("input", truncateForEmbedding(input));
    body.put("dimensions", embeddingDimensions);
    try {
      String raw =
          client
              .post()
              .uri("/embeddings")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(String.class);
      if (raw == null || raw.isBlank()) {
        return new float[0];
      }
      JsonNode root = objectMapper.readTree(raw);
      JsonNode data = root.path("data");
      if (!data.isArray() || data.isEmpty()) {
        return new float[0];
      }
      JsonNode emb = data.get(0).path("embedding");
      if (!emb.isArray()) {
        return new float[0];
      }
      List<Float> floats = new ArrayList<>();
      for (JsonNode n : emb) {
        floats.add((float) n.asDouble());
      }
      float[] out = new float[floats.size()];
      for (int i = 0; i < floats.size(); i++) {
        out[i] = floats.get(i);
      }
      return out;
    } catch (RestClientException e) {
      log.warn("Embedding API request failed: {}", e.getMessage());
      return new float[0];
    } catch (Exception e) {
      log.warn("Embedding API parse failed", e);
      return new float[0];
    }
  }

  private static String truncateForEmbedding(String input) {
    if (input.length() <= 30000) {
      return input;
    }
    return input.substring(0, 30000);
  }

  public boolean isConfigured() {
    return StringUtils.hasText(apiKey);
  }

  public int dimension() {
    return embeddingDimensions;
  }
}
