-- Hybrid-retrieval store (app.retrieval.mode=hybrid): dense vector + full-text + RBAC.
-- ${embeddingDimensions} / ${textSearchConfig} are Flyway placeholders fed by the same
-- config as the app (EMBEDDING_DIMENSIONS / RETRIEVAL_TS_CONFIG), so the column and the
-- embedder can never drift.
CREATE TABLE IF NOT EXISTS rag_chunks (
    id            BIGSERIAL PRIMARY KEY,
    document_id   TEXT NOT NULL,
    section_path  TEXT NOT NULL DEFAULT '',
    content       TEXT NOT NULL,
    metadata      JSONB NOT NULL DEFAULT '{}'::jsonb,
    allowed_roles TEXT[] NOT NULL DEFAULT ARRAY['public'],
    embedding     vector(${embeddingDimensions}) NOT NULL,
    content_tsv   tsvector GENERATED ALWAYS AS (to_tsvector('${textSearchConfig}', content)) STORED
);

CREATE INDEX IF NOT EXISTS rag_chunks_tsv_idx       ON rag_chunks USING GIN  (content_tsv);
CREATE INDEX IF NOT EXISTS rag_chunks_roles_idx     ON rag_chunks USING GIN  (allowed_roles);
CREATE INDEX IF NOT EXISTS rag_chunks_embedding_idx ON rag_chunks USING hnsw (embedding vector_cosine_ops);
