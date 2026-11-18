-- Document version tracking (A3): content-hash identity per document and per chunk, so a
-- re-ingest can be classified unchanged / full / partial without re-embedding. Decoupled from
-- the retrieval backend (works in both vector and hybrid modes) — pure metadata, no RBAC.
CREATE TABLE IF NOT EXISTS rag_documents (
    document_id  TEXT PRIMARY KEY,
    doc_hash     TEXT NOT NULL,
    size_bytes   BIGINT NOT NULL DEFAULT 0,
    chunk_count  INT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rag_document_chunks (
    document_id  TEXT NOT NULL REFERENCES rag_documents(document_id) ON DELETE CASCADE,
    section_path TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    ord          INT NOT NULL,
    PRIMARY KEY (document_id, section_path)
);
