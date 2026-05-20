-- Stores every AI request for compliance and billing purposes.
-- We store a hash of the prompt, never the raw prompt text.

CREATE TABLE audit_logs (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    prompt_hash    VARCHAR(64)  NOT NULL,
    tokens_input   INTEGER,
    tokens_output  INTEGER,
    llm_provider   VARCHAR(30),
    latency_ms     INTEGER,
    dlp_triggered  BOOLEAN      NOT NULL DEFAULT FALSE,
    rag_used       BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_tenant_id ON audit_logs (tenant_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
