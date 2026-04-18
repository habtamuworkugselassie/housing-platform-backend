package com.housingplatform.publicsupport.rag;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SupportRagVectorRetrievalService {

  private final SupportRagEmbeddingClient embeddingClient;
  private final SupportRagVectorStore vectorStore;
  private final SupportRagIndexingService indexingService;

  @Value("${app.support-chat.rag-vector-top-k:16}")
  private int topK;

  public boolean isEnabled() {
    return indexingService.isEnabled();
  }

  /**
   * Semantic search over indexed chunks; returns a block suitable for the LLM system prompt, or
   * empty if disabled / no API key / no index.
   */
  public String retrieveFormatted(String userMessage) {
    if (!isEnabled() || !StringUtils.hasText(userMessage)) {
      return "";
    }
    float[] q = embeddingClient.embed(userMessage);
    if (q.length == 0) {
      return "";
    }
    List<SupportRagVectorStore.RagSearchHit> hits = vectorStore.search(q, topK);
    if (hits.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(
        "VECTOR_RAG_HITS (semantic retrieval over indexed marketplace knowledge; cite these lines; "
            + "distances are cosine — lower is closer):\n");
    for (SupportRagVectorStore.RagSearchHit h : hits) {
      sb.append("- distance=").append(String.format(java.util.Locale.ROOT, "%.4f", h.distance()));
      sb.append(" | ").append(h.content()).append("\n");
    }
    return sb.toString().trim();
  }
}
