-- Baseline: extensions only. Tables arrive in later migrations.
-- Requires pgvector to be installed on the server (the pgvector/pgvector image provides it).
CREATE EXTENSION IF NOT EXISTS vector;
