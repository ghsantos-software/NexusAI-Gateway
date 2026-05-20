# NexusAI Gateway

A backend study project inspired by the kinds of AI gateway systems used in corporate environments. Built to practice Spring AI, pgvector, multi-tenancy, and a few security/compliance patterns in a realistic context.

The core idea: instead of letting applications call LLM APIs directly, they go through this gateway, which strips sensitive data from prompts, injects relevant company context, calls the model, and keeps an audit trail — all in one service.

---

## Why I built this

I wanted to go beyond simple CRUD projects and explore how AI integrations actually work in the backend. This project gave me hands-on experience with:

- Spring AI and how to abstract LLM providers
- pgvector and semantic search with raw SQL (Hibernate doesn't support vector types natively)
- Multi-tenant data isolation without schema-per-tenant complexity
- Regex-based data masking (Brazilian data types: CPF, phone, credit card)
- JWT + Spring Security 6 stateless auth
- Rate limiting with Bucket4j and cache with Caffeine
- Testcontainers for integration tests against a real PostgreSQL

---

## How it works

When a request comes in through `POST /api/v1/ai/chat`:

```
Request
  → JWT auth (Spring Security)
  → Rate limiter (Bucket4j — per tenant, plan-based limits)
  → DLP masking (CPF, email, phone, credit card replaced with [REDACTED] tokens)
  → Cache check (Caffeine — skipped if RAG is enabled)
  → RAG context injection (pgvector similarity search on uploaded documents)
  → LLM call (Ollama or OpenAI, switchable via env var)
  → Async audit log (SHA-256 hash of prompt — raw text never stored)
  → Response
```

---

## Tech stack

| | |
|---|---|
| Java 21 | Records, text blocks, pattern matching |
| Spring Boot 3.2 | Main framework |
| Spring AI 1.0 | LLM abstraction (Ollama + OpenAI) |
| PostgreSQL 16 + pgvector | Relational DB + vector similarity search |
| Flyway 10 | Database migrations |
| Spring Security 6 + JWT | Stateless auth (JJWT 0.12) |
| Caffeine | In-memory cache |
| Bucket4j 8 | Rate limiting per tenant |
| Micrometer + Prometheus | Metrics |
| Springdoc OpenAPI 3 | Swagger UI |
| JUnit 5 + Mockito | Unit tests |
| Testcontainers | Integration tests with real PostgreSQL |
| Docker | Local dev environment |

---

## Project structure

```
src/main/java/com/nexusai/gateway/
├── ai/              # Chat endpoint, LLM integration, DLP+cache+RAG pipeline
│   ├── config/      # LlmConfig (provider selection), AiProperties
│   └── dto/         # ChatRequest, ChatResponse
├── audit/           # Async audit log — stores prompt hash, token counts, latency
├── auth/            # JWT filter, UserDetails, register/login, user endpoints
│   ├── dto/
│   └── model/
├── config/          # SecurityConfig, CacheConfig, AsyncConfig, OpenAPI, health
├── dlp/             # Regex-based masking (CPF, email, phone, card)
├── rag/             # Document upload, text chunking, pgvector search
│   └── dto/
├── ratelimit/       # Bucket4j filter (per-tenant, plan-based)
├── shared/          # TenantAwareEntity, ApiResponse<T>, PageResponse<T>
└── tenant/          # Tenant entity, plan, TenantContext (ThreadLocal)
```

---

## Running locally

**Requirements:** Java 21, Maven, Docker

### 1. Clone the project

```bash
git clone https://github.com/ghsantos-software/NexusAI-Gateway.git
cd NexusAI-Gateway
```

### 2. Start PostgreSQL and Ollama

```bash
docker compose up -d
```

### 3. Pull a model (first time only)

```bash
docker exec nexusai-ollama ollama pull llama3.2:1b
```

This downloads a ~1GB model that runs locally. No API key needed.

### 4. Run the application

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 5. Quick test

```bash
# Register (creates a tenant automatically)
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"companyName":"Acme Corp","email":"admin@acme.com","password":"password123"}' | jq .

# Copy the token from the response and use it:
curl -s -X POST http://localhost:8080/api/v1/ai/chat \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"What is the capital of Brazil?"}' | jq .
```

---

## Configuration

All sensitive config uses environment variables with safe defaults for local dev. **Never use the defaults in production.**

Copy the example file and fill in your values:

```bash
cp .env.example .env
```

| Variable | Default (dev only) | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/nexusai` | PostgreSQL URL |
| `DB_USER` | `nexusai` | DB user |
| `DB_PASSWORD` | `nexusai` | DB password |
| `JWT_SECRET` | *(dev placeholder)* | Min 32 chars — change for production |
| `JWT_EXPIRATION` | `86400000` | Token TTL in ms (24h) |
| `AI_PROVIDER` | `ollama` | `ollama` or `openai` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `llama3.2:1b` | Model to use |
| `OPENAI_API_KEY` | *(empty)* | Required when `AI_PROVIDER=openai` |

### Using OpenAI

```bash
# In your .env file:
AI_PROVIDER=openai
OPENAI_API_KEY=sk-your-key-here
```

> **Note about RAG:** Even when using Ollama for chat, the RAG feature requires an OpenAI API key because embeddings always use `text-embedding-3-small` (1536 dimensions). This avoids dimension mismatches with the pgvector column.

---

## API overview

### Auth

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a new company account (creates tenant + admin user) |
| `POST` | `/api/v1/auth/login` | Login, returns JWT |

### AI Gateway

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/ai/chat` | Send a chat message through the full pipeline |

**Request body:**
```json
{
  "message": "Customer CPF 123.456.789-00 wants a refund",
  "systemPrompt": "Optional override for the system prompt",
  "useRag": true
}
```

**Response:**
```json
{
  "content": "Based on our refund policy...",
  "provider": "ollama",
  "tokensInput": 45,
  "tokensOutput": 120,
  "latencyMs": 1834,
  "dlpApplied": true,
  "ragUsed": false
}
```

### RAG (Document Management)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/rag/documents` | Upload a .txt or .pdf file |
| `GET` | `/api/v1/rag/documents` | List uploaded documents |
| `DELETE` | `/api/v1/rag/documents/{name}` | Delete a document and its vectors |

### Other

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/users/me` | Current user profile |
| `GET` | `/api/v1/users` | List tenant users (admin only) |
| `GET` | `/api/v1/audit` | Audit log, paginated (admin only) |
| `GET` | `/actuator/health` | App health + Ollama status |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

Full collection: `NexusAI-Gateway.postman_collection.json` (import into Postman — register/login auto-save the token).

---

## Running tests

```bash
# Docker must be running — Testcontainers spins up a real PostgreSQL container
mvn test
```

The integration tests in `AbstractIntegrationTest` use the `pgvector/pgvector:pg16` Docker image via Testcontainers, so they run against real migrations without needing a pre-existing database.

---

## Multi-tenancy

Every registered company gets its own `tenant_id`. All tables have a `tenant_id` column and every query filters by it. The tenant is extracted from the JWT and stored in a `ThreadLocal` for the duration of each request.

No schema-per-tenant: simpler to operate, and row-level isolation is enough for this use case.

---

## DLP masking

Four patterns applied in order before the prompt reaches the LLM:

| Data type | Example input | Replaced with |
|---|---|---|
| Credit card | `4111 1111 1111 1111` | `[CARD_REDACTED]` |
| Brazilian CPF | `123.456.789-00` | `[CPF_REDACTED]` |
| Email | `user@company.com` | `[EMAIL_REDACTED]` |
| Brazilian phone | `(11) 98765-4321` | `[PHONE_REDACTED]` |

Credit card pattern runs first to avoid partial matches from the phone regex.

---

## Metrics

Three Prometheus metrics exposed at `/actuator/prometheus`:

| Metric | Type |
|---|---|
| `nexusai.requests.total` | Counter (tagged by provider) |
| `nexusai.errors.total` | Counter (tagged by provider) |
| `nexusai.request.duration` | Timer histogram |

---

## Things I'd improve with more time

- **Multi-instance rate limiting**: The current `ConcurrentHashMap` buckets reset on restart. For horizontal scaling, this would need Redis + Bucket4j's `ProxyManager`.
- **Token usage tracking**: The monthly token counter exists in the `tenants` table but billing enforcement isn't wired yet.
- **Monthly token reset**: Would need a scheduled job to reset `tokens_used_this_month` at the billing cycle boundary.
- **`.docx` support**: RAG currently accepts `.txt` and `.pdf`. Adding Word documents via Apache POI would be straightforward.
- **Conversation history**: The AI chat is stateless right now. Multi-turn conversations would require storing and passing message history.
- **Better error messages from Ollama**: When the model isn't pulled yet, the error message could be more specific.

---

## What I learned

- **Spring AI abstractions are genuinely useful**: Switching between Ollama and OpenAI is a single env var change. The `ChatClient` abstraction handles the rest.
- **pgvector + JdbcTemplate**: Hibernate 6 doesn't support custom types like `vector` natively, so raw SQL via `JdbcTemplate` was the right call — not a workaround, just using the right tool.
- **Filter double-registration is a real Spring Boot gotcha**: `@Component` filters auto-register as servlet filters AND inside the Spring Security chain unless you explicitly disable one. This was the trickiest bug to track down.
- **Testcontainers makes integration tests reliable**: No more "works on my machine but not in CI" database tests.
- **SHA-256 for audit logs feels like overkill until you think about it**: Storing raw prompts creates a data liability. Hashing them preserves auditability without keeping the content.

---

## License

MIT
