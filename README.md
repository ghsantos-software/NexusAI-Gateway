# NexusAI Gateway

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-yellow)
![CI](https://img.shields.io/github/actions/workflow/status/ghsantos-software/NexusAI-Gateway/ci.yml?label=build&logo=githubactions&logoColor=white)

A backend study project that simulates an AI gateway — the kind of service that sits between applications and LLM APIs in companies that need audit trails, data protection, and multi-tenant isolation.

Instead of calling OpenAI or Ollama directly, requests go through a pipeline that masks sensitive data, injects relevant context from uploaded documents, and logs a hash of every prompt. All tenants share the same database schema but their data stays isolated via `tenant_id`.

---

## Key Features

- **DLP masking** — CPF, email, phone, and credit card are stripped from prompts before reaching the LLM
- **RAG pipeline** — upload `.txt` or `.pdf` files; relevant chunks are injected via pgvector cosine similarity search
- **Multi-tenancy** — row-level isolation; every registered company gets its own `tenant_id` carried in the JWT
- **Provider-agnostic** — switch between Ollama (local, free) and OpenAI with a single env var
- **Audit log** — async, stores SHA-256 of the prompt — raw text never persisted
- **Rate limiting** — per-tenant, plan-based limits with Bucket4j
- **Response cache** — Caffeine; automatically bypassed when RAG context is active

---

## Architecture Highlights

- **Spring AI abstraction** — `ChatClient` handles both Ollama and OpenAI behind the same interface; switching providers is one env var, zero code changes
- **DLP before the network** — prompts are masked locally before any HTTP call; the LLM never sees real CPFs, emails, or card numbers
- **pgvector via JdbcTemplate** — Hibernate 6 doesn't support `vector` types natively; raw SQL with cosine distance (`<=>`) is cleaner than fighting the ORM
- **Row-level multi-tenancy** — single schema, `tenant_id` on every table, value extracted from JWT and stored in `ThreadLocal` per request
- **Hash-only audit trail** — SHA-256 of each prompt is stored, never the raw text; auditability without data liability
- **Smart cache invalidation** — Caffeine cache is skipped when RAG is active, since injected context changes per query

---

## Request Pipeline

```
 Client
   │
   ▼
 ┌─────────────────────────────────────────┐
 │  JWT validation   (Spring Security 6)   │
 ├─────────────────────────────────────────┤
 │  Rate limiter     (Bucket4j)            │  per tenant, plan-based
 ├─────────────────────────────────────────┤
 │  DLP masking                            │  CPF, email, phone, card → [REDACTED]
 ├─────────────────────────────────────────┤
 │  Cache lookup     (Caffeine)            │  skipped when RAG is active
 ├─────────────────────────────────────────┤
 │  RAG context injection  (pgvector)      │  cosine similarity, threshold-filtered
 ├─────────────────────────────────────────┤
 │  LLM call         (Spring AI)           │  Ollama or OpenAI
 ├─────────────────────────────────────────┤
 │  Async audit log                        │  SHA-256 hash, token count, latency
 └─────────────────────────────────────────┘
   │
   ▼
 Response
```

→ [Architecture diagram](docs/architecture.md) · [Pipeline flowchart](docs/request-pipeline.md)

---

## Tech Stack

| | |
|---|---|
| **Java 21** | Records, text blocks, pattern matching |
| **Spring Boot 3.3** | Core framework |
| **Spring AI 1.0** | LLM abstraction — same code, different provider |
| **PostgreSQL 16 + pgvector** | Relational DB + vector similarity search |
| **Flyway 10** | Database migrations |
| **Spring Security 6 + JJWT 0.12** | Stateless JWT auth |
| **Caffeine + Bucket4j 8** | Response cache and per-tenant rate limiting |
| **Micrometer + Prometheus** | Metrics |
| **Springdoc OpenAPI 3** | Swagger UI |
| **JUnit 5 + Mockito** | Unit and integration tests |
| **Docker + docker-compose** | Local dev environment |

---

## Project Structure

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

## Running Locally

**Requirements:** Java 21, Docker

### 1. Clone

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

Downloads ~1GB locally. No API key needed.

### 4. Run

```bash
./mvnw spring-boot:run
```

App starts on `http://localhost:8080`

| Interface | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Prometheus metrics | `http://localhost:8080/actuator/prometheus` |
| Health check | `http://localhost:8080/actuator/health` |

### 5. Quick test

```bash
# Register — creates a tenant automatically
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"companyName":"Acme Corp","email":"admin@acme.com","password":"password123"}' | jq .

# Send a chat message with sensitive data — watch DLP kick in
curl -s -X POST http://localhost:8080/api/v1/ai/chat \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"Customer CPF 123.456.789-00 needs a refund"}' | jq .
```

---

## Configuration

Copy the example and fill in your values:

```bash
cp .env.example .env
```

| Variable | Default (dev) | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/nexusai` | PostgreSQL URL |
| `DB_USER` | `nexusai` | DB user |
| `DB_PASSWORD` | `nexusai` | DB password |
| `JWT_SECRET` | *(dev placeholder)* | **Min 32 chars in production** |
| `JWT_EXPIRATION` | `86400000` | Token TTL in ms (24h) |
| `AI_PROVIDER` | `ollama` | `ollama` or `openai` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `llama3.2:1b` | Model to use |
| `OPENAI_API_KEY` | *(empty)* | Required when `AI_PROVIDER=openai` |

> **RAG + embeddings:** even when using Ollama for chat, RAG always uses OpenAI `text-embedding-3-small` (1536 dimensions). This keeps the pgvector column dimensions consistent across providers. Requires `OPENAI_API_KEY`.

---

## API Reference

### Auth

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register company — creates tenant + admin user |
| `POST` | `/api/v1/auth/login` | Login, returns JWT |

### AI Gateway

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/ai/chat` | Send a message through the full pipeline |

**Request:**
```json
{
  "message": "Customer CPF 123.456.789-00 wants a refund",
  "systemPrompt": "Optional system prompt override",
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

### Documents (RAG)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/rag/documents` | Upload `.txt` or `.pdf` |
| `GET` | `/api/v1/rag/documents` | List uploaded documents |
| `DELETE` | `/api/v1/rag/documents/{name}` | Delete document and its vectors |

### Other

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/users/me` | Current user profile |
| `GET` | `/api/v1/users` | List tenant users (admin only) |
| `GET` | `/api/v1/audit` | Audit log, paginated (admin only) |
| `GET` | `/actuator/health` | App health + Ollama connectivity |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

Full Postman collection: `NexusAI-Gateway.postman_collection.json` — register and login auto-save the token.

---

## Running Tests

```bash
# Start postgres first
docker compose up -d postgres

# Run all tests
./mvnw test
```

Integration tests extend `AbstractIntegrationTest`, which connects to the docker-compose postgres using the `test` profile. The database is truncated before each test class. No external dependencies needed beyond Docker.

CI runs the same test suite automatically on every push — see [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

---

## Technical Notes

### DLP masking

Four regex patterns applied in order before the prompt leaves the service:

| Type | Example | Replaced with |
|---|---|---|
| Credit card | `4111 1111 1111 1111` | `[CARD_REDACTED]` |
| CPF | `123.456.789-00` | `[CPF_REDACTED]` |
| Email | `user@company.com` | `[EMAIL_REDACTED]` |
| BR phone | `(11) 98765-4321` | `[PHONE_REDACTED]` |

Credit card runs first to avoid false positives from the phone regex matching card digit sequences.

### Multi-tenancy

Row-level isolation with a single schema. Every table has `tenant_id`, every query filters by it. The value is extracted from the JWT at the security filter and stored in `ThreadLocal` for the duration of the request. No schema-per-tenant overhead.

### pgvector

Hibernate 6 doesn't support custom column types like `vector`. Vector operations (insert, cosine similarity search) use `JdbcTemplate` with raw SQL — explicit, readable, no ORM friction.

### Metrics

Exposed at `/actuator/prometheus`:

| Metric | Type |
|---|---|
| `nexusai.requests.total` | Counter (tagged by provider) |
| `nexusai.errors.total` | Counter (tagged by provider) |
| `nexusai.request.duration` | Timer histogram |

---

## Things I'd Improve

- **Rate limiting across instances** — current `ConcurrentHashMap` buckets reset on restart; horizontal scaling needs Redis + Bucket4j's `ProxyManager`
- **Token billing enforcement** — `tokens_used_this_month` exists in the DB but the limit isn't enforced yet; needs a scheduled reset job too
- **Conversation history** — chat is stateless; multi-turn requires a message store and context window management
- **`.docx` support** — straightforward Apache POI addition; `.txt` and `.pdf` already work via PDFBox
- **Ollama error messages** — when the model isn't pulled yet, the error could be more descriptive than a generic 500

---

## What I Learned

**Spring AI abstractions are genuinely useful.** Switching between Ollama and OpenAI is a single env var — no code changes. The `ChatClient` handles provider differences transparently.

**pgvector with JdbcTemplate is the right call.** When Hibernate doesn't support a type, raw SQL is cleaner than fighting the ORM. The cosine distance query is readable and explicit.

**Spring Security filter double-registration is a real gotcha.** A `@Component` filter auto-registers as both a servlet filter and inside the security chain unless you explicitly disable one. Tracking that down was the trickiest debugging session in the project.

**SHA-256 for audit logs is the right tradeoff.** Storing raw prompts creates a data liability. Hashing gives you auditability and duplicate detection without keeping the content.

**Row-level multi-tenancy is simpler than it sounds.** The hardest part is being consistent — every query needs the tenant filter. A `TenantAwareEntity` base class and ThreadLocal context handle most of it automatically.

---

## License

MIT
