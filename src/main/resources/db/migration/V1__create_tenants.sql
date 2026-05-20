CREATE TABLE tenants (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(100)  NOT NULL,
    slug                    VARCHAR(50)   NOT NULL UNIQUE,
    plan                    VARCHAR(20)   NOT NULL DEFAULT 'FREE',
    monthly_token_limit     INTEGER       NOT NULL DEFAULT 100000,
    tokens_used_this_month  INTEGER       NOT NULL DEFAULT 0,
    llm_provider            VARCHAR(30)   NOT NULL DEFAULT 'OPENAI',
    llm_api_key_encrypted   TEXT,
    active                  BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenants_slug ON tenants (slug);

ALTER TABLE tenants ADD CONSTRAINT chk_tokens_non_negative
    CHECK (tokens_used_this_month >= 0);
