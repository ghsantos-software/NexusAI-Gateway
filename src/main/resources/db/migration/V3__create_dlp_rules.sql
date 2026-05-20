CREATE TABLE dlp_rules (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    pattern_type   VARCHAR(50)  NOT NULL,
    regex_pattern  TEXT,
    action         VARCHAR(20)  NOT NULL DEFAULT 'REDACT',
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dlp_rules_tenant_id ON dlp_rules (tenant_id);
