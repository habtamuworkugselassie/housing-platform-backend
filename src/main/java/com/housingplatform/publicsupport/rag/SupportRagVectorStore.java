package com.housingplatform.publicsupport.rag;

import com.pgvector.PGvector;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SupportRagVectorStore {

  private final JdbcTemplate jdbcTemplate;

  public record RagSearchHit(
      SupportRagSourceType sourceType,
      UUID sourceId,
      int chunkIndex,
      String content,
      double distance) {}

  public void upsert(
      UUID id,
      SupportRagSourceType sourceType,
      UUID sourceId,
      int chunkIndex,
      String content,
      String contentHash,
      float[] embedding) {
    PGvector vec = new PGvector(embedding);
    jdbcTemplate.update(
        """
        INSERT INTO support_rag_chunks (id, source_type, source_id, chunk_index, content, content_hash, embedding, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
        ON CONFLICT (source_type, source_id, chunk_index) DO UPDATE SET
          content = EXCLUDED.content,
          content_hash = EXCLUDED.content_hash,
          embedding = EXCLUDED.embedding,
          updated_at = NOW()
        """,
        id,
        sourceType.name(),
        sourceId,
        chunkIndex,
        content,
        contentHash,
        vec);
  }

  public String findContentHash(SupportRagSourceType sourceType, UUID sourceId, int chunkIndex) {
    List<String> rows =
        jdbcTemplate.query(
            "SELECT content_hash FROM support_rag_chunks WHERE source_type = ? AND source_id = ? AND chunk_index = ?",
            (rs, rowNum) -> rs.getString(1),
            sourceType.name(),
            sourceId,
            chunkIndex);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public void delete(SupportRagSourceType sourceType, UUID sourceId) {
    jdbcTemplate.update(
        "DELETE FROM support_rag_chunks WHERE source_type = ? AND source_id = ?",
        sourceType.name(),
        sourceId);
  }

  public List<RagSearchHit> search(float[] queryEmbedding, int limit) {
    if (queryEmbedding == null || queryEmbedding.length == 0 || limit <= 0) {
      return List.of();
    }
    PGvector q = new PGvector(queryEmbedding);
    return jdbcTemplate.query(
        """
        SELECT source_type, source_id, chunk_index, content, embedding <=> ?::vector AS distance
        FROM support_rag_chunks
        ORDER BY embedding <=> ?::vector
        LIMIT ?
        """,
        (rs, rowNum) ->
            new RagSearchHit(
                SupportRagSourceType.valueOf(rs.getString("source_type")),
                UUID.fromString(rs.getString("source_id")),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("distance")),
        q,
        q,
        limit);
  }

  public long count() {
    Long cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_rag_chunks", Long.class);
    return cnt != null ? cnt : 0L;
  }
}
