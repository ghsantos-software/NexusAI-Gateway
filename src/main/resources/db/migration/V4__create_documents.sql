CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunks (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    document_name  VARCHAR(200) NOT NULL,
    chunk_index    INTEGER      NOT NULL,
    content        TEXT         NOT NULL,
    embedding      vector(1536),  -- OpenAI text-embedding-3-small
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_chunks_tenant_id ON document_chunks (tenant_id);

-- IVFFlat index for approximate nearest neighbor search (create after initial data load)
-- CREATE INDEX ON document_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
