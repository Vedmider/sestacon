-- Document lifecycle: delete/replace operate by document_id (supersession — re-ingesting a
-- document overwrites it). A non-unique btree index keeps those WHERE document_id = ? lookups
-- and deletes fast as the corpus grows.
CREATE INDEX IF NOT EXISTS rag_chunks_document_id_idx ON rag_chunks (document_id);
