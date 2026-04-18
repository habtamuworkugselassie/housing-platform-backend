-- Prerequisite (run once, outside Flyway, as a superuser or via your host's dashboard):
--   CREATE EXTENSION IF NOT EXISTS vector;
-- Without this, the vector type below will not exist. Managed Postgres (e.g. Render) usually
-- lets you enable "pgvector" / "vector" in the database settings UI — do that before migrating.
-- Local Docker: pgvector/pgvector image includes the extension; enable it before Flyway runs.
--
-- We do not run CREATE EXTENSION here: the Flyway user is often not a superuser.

CREATE TABLE IF NOT EXISTS support_rag_chunks (
    id UUID PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    chunk_index INT NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding vector(1536) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_support_rag_chunks_source UNIQUE (source_type, source_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_support_rag_chunks_source
    ON support_rag_chunks (source_type, source_id);

-- Cosine distance; suitable for OpenAI-style normalized embeddings
CREATE INDEX IF NOT EXISTS idx_support_rag_chunks_embedding
    ON support_rag_chunks USING hnsw (embedding vector_cosine_ops);
