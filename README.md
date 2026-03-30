# Power RAG

A production-grade **Retrieval-Augmented Generation (RAG)** platform with a React frontend, Spring Boot backend, and first-class support for multiple LLM providers (Anthropic Claude, Google Gemini, Ollama local models).

---

## 🌐 Live Demo

A running instance of Power RAG is deployed on Google Cloud (Singapore region):

| | |
|---|---|
| **Demo URL** | <a href="https://power-rag.cheeyonglee.com" target="_blank">https://power-rag.cheeyonglee.com ↗</a> |
| **Login** | `admin` / `Admin@1234` |
| **Region** | `asia-southeast1` (Singapore) |
| **Infrastructure** | GCE `e2-standard-2`, Debian 12, Docker Compose |
| **TLS** | Signed by Let's Encrypt (auto-renews every 90 days) |

---

## Table of Contents

1. [Live Demo](#-live-demo)
2. [Features](#features)
3. [Architecture Overview](#architecture-overview)
4. [Technology Stack](#technology-stack)
5. [Project Structure](#project-structure)
6. [Prerequisites](#prerequisites)
7. [Quick Start (Docker)](#quick-start-docker)
8. [Local Development Setup](#local-development-setup)
9. [Configuration Reference](#configuration-reference)
10. [API Reference](#api-reference)
11. [Database Schema](#database-schema)
12. [LLM Providers](#llm-providers)
13. [Security](#security)
14. [Testing](#testing)
15. [Deployment](#deployment)
16. [Training Materials](#training-materials)
17. [Contributing](#contributing)

---

## Features

| Category | Capability |
|---|---|
| **RAG Pipeline** | Hybrid dense (Qdrant) + keyword (PostgreSQL FTS) retrieval with Reciprocal Rank Fusion |
| **Document Ingestion** | PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), Java source, images (PNG/JPG/GIF/WebP) |
| **Multimodal** | Paste images directly into chat; multimodal analysis via Claude / Gemini |
| **Image Generation** | Generate images via Google Imagen 3 or Gemini Flash (intent detection from natural language) |
| **Multi-LLM** | Claude Opus/Sonnet/Haiku, Gemini 2.5 Pro/Flash/Flash-Lite, Ollama (Qwen, DeepSeek) |
| **Semantic Cache** | Redis-backed vector cache (cosine threshold 0.92, 24 h TTL) eliminates redundant LLM calls |
| **Guardrails** | Input safety via **Gemini 2.5 Flash** (`gemini-2.5-flash`); output PII detection via regex + redaction |
| **MCP Tool Integration** | Live data via Model Context Protocol: web fetch, time/weather, Jira Cloud, GitHub code search, GCP Cloud Logging, IMAP/SMTP email |
| **Intent Routing** | Fast LLM classifier decides per-query whether to search the knowledge base and/or attach live tools — reduces unnecessary retrieval latency |
| **Text-to-SQL** | Natural language → validated, read-only SQL against a live PostgreSQL schema |
| **Multilingual** | English, Simplified Chinese, Traditional Chinese UI + LLM response language |
| **Auth** | JWT-based auth with `USER` / `ADMIN` roles |
| **Audit Log** | Every interaction persisted to PostgreSQL with model, confidence, sources, duration, and MCP tool invocation summaries (JSONB) |
| **Feedback** | Thumbs-up/down + star ratings stored and surfaced in admin dashboard |
| **Admin Dashboard** | Interaction history, guardrail flags, model usage metrics |

---

## Architecture Overview

```
Browser (React 18 + Vite)
│
│  HTTPS via Nginx reverse proxy (port 3443)
│
├─► POST /api/chat/query
│        │
│        ▼
│   RagService (Spring Boot 3.5)
│   ┌────────────────────────────────────────────────────────────────┐
│   │ 1. Input Guardrail   (Gemini 2.5 Flash via Google GenAI)      │
│   │ 2. Semantic Cache    (Redis Stack / RedisVectorStore)          │
│   │ 3. Image Generation  (Imagen 3 / Gemini Flash-img)            │
│   │ 4. Intent Routing    (QueryIntentClassifier — LLM or heuristic)│
│   │    → decides: retrieve KB? attach MCP tools?                  │
│   │ 5. Hybrid Retrieval  (conditional on intent)                  │
│   │    ├─ Dense:   Qdrant vector search                           │
│   │    └─ Keyword: PostgreSQL full-text search                    │
│   │    └─ Merge:   Reciprocal Rank Fusion (k=60)                  │
│   │ 6. Context Assembly  (max 24 000 chars)                       │
│   │ 7. LLM Call          (provider + model selected)              │
│   │    └─ MCP Tools      (optional: web/Jira/GitHub/GCP/email)    │
│   │ 8. Output Guardrail  (PII redaction)                          │
│   │ 9. Cache Store       (Redis)                                  │
│   │10. Audit Log         (PostgreSQL + MCP invocations JSONB)     │
│   └────────────────────────────────────────────────────────────────┘
│
├─► POST /api/sql/query
│        │
│        ▼
│   TextToSqlService → Gemini 2.5 Pro → SqlValidator → JDBC
│
└─► POST /api/documents/upload
         │
         ▼
    DocumentIngestionService
    ├─ Parser     (PDF/Word/Excel/PPT/Java/Image)
    ├─ Chunker    (Sliding-window, 512 words, 64 overlap)
    ├─ Embedder   (gemini-embedding-001 via Google GenAI, 768-dim)
    ├─ Qdrant     (vector storage)
    └─ PostgreSQL (chunk metadata + FTS)
```

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                      │
│  PostgreSQL 16  │  Qdrant 1.13  │  Redis Stack 7.4          │
│  (relational)     (vector DB)     (semantic cache)          │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                      AI / LLM Layer                         │
│  Ollama (local)    │  Anthropic API  │  Google AI Studio    │
│  ├ qwen2.5-coder      ├ claude-opus      ├ gemini-2.5-pro   │
│  └ deepseek-coder     ├ claude-sonnet    ├ gemini-2.5-flash  │
│                       └ claude-haiku     ├ gemini-embedding-001 (KB + cache) │
│                                          ├ imagen-3.0        │
│                                          └ gemini-flash-img  │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                        │
│  Spring Boot 3.5 (JDK 25)  │  React 18 + Vite + TailwindCSS│
│  Spring AI 1.1.2            │  TanStack Query + i18next     │
└─────────────────────────────────────────────────────────────┘
```

---

## Technology Stack

### Backend

| Component | Version | Purpose |
|---|---|---|
| Java | 25 (JDK 25) | Runtime |
| Spring Boot | 3.5.11 | Framework |
| Spring AI | 1.1.2 | LLM / vector store abstraction |
| Spring Security | (Boot managed) | JWT auth |
| Flyway | (Boot managed) | DB migrations |
| PostgreSQL JDBC | (Boot managed) | Relational persistence |
| Apache PDFBox | 3.0.3 | PDF parsing |
| Apache POI | 5.3.0 | Excel/Word/PPT parsing |
| JavaParser | 3.26.1 | Java source parsing |
| Lombok | 1.18.38 | Boilerplate reduction |
| JaCoCo | 0.8.14 | Code coverage |
| Cucumber | 7.20.1 | BDD integration tests |

### Frontend

| Component | Version | Purpose |
|---|---|---|
| React | 18.3 | UI framework |
| Vite | 6.0 | Build tool |
| TypeScript | 5.6 | Type safety |
| TailwindCSS | 3.4 | Styling |
| TanStack Query | 5.62 | Server state management |
| Axios | 1.7 | HTTP client |
| i18next | 24.2 | Internationalisation |
| Framer Motion | 11.15 | Animations |
| Radix UI | various | Accessible components |
| Vitest | 2.1 | Unit testing |
| Playwright | 1.49 | E2E testing |

### Infrastructure

| Service | Version | Purpose |
|---|---|---|
| PostgreSQL | 16-alpine | Relational DB (users, docs, interactions, grants) |
| Qdrant | 1.13.4 | Vector database for embeddings |
| Redis Stack | 7.4.0 | Semantic cache (RedisVectorStore) |
| Ollama | latest | Local chat models (optional; embeddings use Google GenAI) |

---

## Project Structure

```
power_rag/
├── .env.example                   # Environment variable template
├── .gitignore
├── docker-compose.yml             # Full stack: PG + Redis + Qdrant + Ollama + App
├── nginx/
│   └── nginx.conf                 # HTTPS reverse proxy (ports 3443, 8443)
├── ssl/                           # Self-signed certs for local HTTPS (not committed)
│
├── backend/                       # Spring Boot application
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/powerrag/
│       ├── PowerRagApplication.java
│       ├── api/                   # REST controllers
│       │   ├── AuthController.java
│       │   ├── ChatController.java
│       │   ├── DocumentController.java
│       │   ├── FeedbackController.java
│       │   ├── AdminController.java
│       │   ├── SqlController.java
│       │   ├── OllamaModelsController.java
│       │   └── UserPreferencesController.java
│       ├── cache/                 # Semantic cache (Redis)
│       ├── config/                # Spring beans (AI, security, vector store)
│       ├── domain/                # JPA entities + Spring Data repositories
│       ├── feedback/              # Feedback service
│       ├── guardrails/            # Input safety (Gemini Flash) + output PII
│       ├── ingestion/             # Document parsing + chunking + embedding
│       │   ├── parser/            # PDF, Word, Excel, PPT, Java, Image parsers
│       │   ├── chunking/          # SlidingWindowChunkingStrategy
│       │   └── service/           # DocumentIngestionService
│       ├── mcp/                   # MCP observability layer
│       │   ├── ObservingToolCallback.java    # Times + records every tool call
│       │   ├── McpInvocationRecorder.java    # Thread-local invocation buffer
│       │   └── McpToolInvocationSummary.java # Per-call data record
│       ├── multilingual/          # Language detection + prompt building
│       ├── rag/                   # Core RAG pipeline
│       │   ├── assembly/          # ContextAssembler
│       │   ├── intent/            # QueryIntentClassifier, QueryIntent
│       │   ├── model/             # RagResponse, RetrievedChunk, SourceRef
│       │   ├── retrieval/         # HybridRetriever (Qdrant + PG FTS + RRF)
│       │   ├── scoring/           # ConfidenceScorer
│       │   └── service/           # RagService, ImageGenerationService
│       ├── security/              # JWT filter, service, UserDetailsService
│       └── sql/                   # Text-to-SQL (SchemaIntrospector, TextToSqlService)
│   ├── mcp/                       # MCP server scripts and binaries
│   │   ├── powerrag_mcp_tools.py  # Python stdio MCP server (fetch, Jira, GitHub, GCP)
│   │   ├── requirements.txt       # Python deps: mcp, httpx, google-auth
│   │   └── bin/mcp-server-email   # Pre-compiled email IMAP/SMTP MCP server
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml    # MCP STDIO connections (dev profile)
│       └── db/migration/          # Flyway V1–V8 SQL migrations
│
└── frontend/                      # React + Vite application
    ├── Dockerfile
    ├── package.json
    └── src/
        ├── api/                   # Axios API clients (auth, chat, documents, sql, admin)
        ├── components/            # Reusable UI components
        │   ├── ChatWindow.tsx     # Main chat area (image paste, MCP badge, generation display)
        │   ├── McpToolsPanel.tsx  # Sidebar panel listing available MCP tools
        │   ├── ModelSelector.tsx  # LLM provider/model picker
        │   ├── UploadZone.tsx     # Drag-and-drop document upload
        │   └── AdminDashboard.tsx # Admin stats + interaction log
        ├── contexts/
        │   └── AuthContext.tsx    # JWT auth state + login/logout
        ├── i18n/                  # en / zh-CN / zh-TW translations
        ├── pages/
        │   ├── LoginPage.tsx
        │   ├── ChatPage.tsx       # Main layout (sidebar + chat + sources)
        │   ├── SqlPage.tsx        # Text-to-SQL interface
        │   └── AdminPage.tsx
        ├── utils/
        │   └── mcpTools.ts        # MCP tool capability helpers (Jira detection etc.)
        └── test/                  # Vitest unit + MSW mocks + Playwright E2E
```

---

## Prerequisites

Before you begin, ensure you have the following installed:

| Tool | Min Version | Notes |
|---|---|---|
| Docker Desktop | 4.x | Required for all infrastructure services |
| Docker Compose | 2.x | Bundled with Docker Desktop |
| Java JDK | 25 | [Homebrew](https://formulae.brew.sh/formula/openjdk): `brew install openjdk` |
| Maven | 3.9+ | Or use the Maven wrapper (`./mvnw`) if present |
| Node.js | 20+ | [nvm](https://github.com/nvm-sh/nvm) recommended |
| npm | 10+ | Bundled with Node.js |
| Ollama | latest | [ollama.ai](https://ollama.ai) |

**API Keys required:**

| Provider | Sign-up | Used for |
|---|---|---|
| [Anthropic](https://console.anthropic.com) | Free trial available | Claude Opus, Sonnet, Haiku |
| [Google AI Studio](https://aistudio.google.com) | Free tier available | Gemini chat/Flash, **embeddings** (`gemini-embedding-001`), **input guardrails** (`gemini-2.5-flash`), Imagen 3 |

---

## Quick Start (Docker)

The fastest way to run the full stack:

```bash
# 1. Clone the repository
git clone https://github.com/<your-org>/power-rag.git
cd power-rag

# 2. Create your environment file
cp .env.example .env
# Edit .env — fill in ANTHROPIC_API_KEY, GOOGLE_API_KEY, JWT_SECRET

# 3. Pull and start all services
docker compose up -d

# 4. Pull Ollama chat models if you use local LLMs (optional — ~GB-scale download)
docker exec powerrag-ollama ollama pull qwen2.5-coder:7b
# Knowledge-base vectors and semantic cache use Google gemini-embedding-001; input guardrails use gemini-2.5-flash — set GOOGLE_API_KEY in .env.

# 5. Open the app
open http://localhost:3000
# Default credentials: admin / Admin@1234
```

> **Note:** The backend waits for all services to be healthy before starting. First boot may take 2–3 minutes. Monitor with `docker compose logs -f backend`.

---

## Local Development Setup

For active development with hot-reload on both frontend and backend.

### Step 1 — Infrastructure services

```bash
# Start only the backing services (no app containers)
docker compose up -d postgres redis-stack qdrant ollama

# Wait for health checks to pass
docker compose ps
```

### Step 2 — (Optional) Pull Ollama chat models

```bash
ollama pull qwen2.5-coder:7b   # Default local chat model (optional if you only use Claude/Gemini)
```

Embeddings for Qdrant ingestion and the semantic cache, plus input guardrails, use the **Google GenAI** API (`GOOGLE_API_KEY`). Ollama embedding autoconfiguration is disabled in this project.

### Step 3 — Backend

```bash
cd backend

# Export API keys (or add to your shell profile)
export ANTHROPIC_API_KEY=sk-ant-...
export GOOGLE_API_KEY=AIza...
export JWT_SECRET=a-long-random-secret-string

# Run with Maven (Flyway migrations run automatically on startup)
./mvnw spring-boot:run
# Backend starts on http://localhost:8080
```

### Step 4 — Frontend

```bash
cd frontend
npm install
npm run dev
# Frontend starts on http://localhost:3000 (or next available port)
```

### Step 5 — (Optional) HTTPS via Nginx

For testing multimodal image features or third-party integrations that require HTTPS:

```bash
# Generate self-signed certificates (macOS)
mkdir -p ssl
openssl req -x509 -nodes -days 365 \
  -newkey rsa:2048 \
  -keyout ssl/localhost.key \
  -out ssl/localhost.crt \
  -subj "/CN=localhost"

# Start Nginx (Homebrew example)
brew install nginx
nginx -c $(pwd)/nginx/nginx.conf

# Access via HTTPS
open https://localhost:3443   # Full app
open https://localhost:8443   # Backend API only
```

### Step 6 — Login

Navigate to `http://localhost:3000` and log in with:

| Field | Value |
|---|---|
| Username | `admin` |
| Password | `Admin@1234` |

> **Change this password** in production by updating the BCrypt hash in `V1__init_schema.sql` or via the admin API.

---

## Configuration Reference

### Backend (`application.yml`)

| Property | Default | Description |
|---|---|---|
| `spring.ai.anthropic.api-key` | `${ANTHROPIC_API_KEY}` | Anthropic API key |
| `spring.ai.anthropic.chat.options.model` | `claude-sonnet-4-6` | Default Claude model |
| `spring.ai.google.genai.api-key` | `${GOOGLE_API_KEY}` | Google AI Studio key |
| `spring.ai.google.genai.chat.options.model` | `gemini-2.5-flash` | Base Gemini model |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Ollama endpoint |
| `spring.ai.ollama.chat.options.model` | `qwen2.5-coder:7b` | Default Ollama chat model |
| `spring.ai.google.genai.embedding.text.options.model` | `gemini-embedding-001` | Embedding model for Qdrant + cache |
| `powerrag.embedding.dimensions` | `768` | Canonical embedding width; drives Gemini embedding + Qdrant (`POWERRAG_EMBEDDING_DIMENSIONS`) |
| `spring.ai.google.genai.embedding.text.options.dimensions` | `${powerrag.embedding.dimensions}` | Same as `powerrag.embedding.dimensions` |
| `powerrag.guardrails.input-model-id` | `gemini-2.5-flash` | Input safety model (override with `POWERRAG_GUARDRAIL_MODEL`) |
| `spring.ai.vectorstore.qdrant.host` | `localhost` | Qdrant host |
| `spring.ai.vectorstore.qdrant.port` | `6334` | Qdrant gRPC port |
| `spring.ai.vectorstore.qdrant.collection-name` | `power_rag_docs` | Qdrant collection |
| `spring.ai.vectorstore.qdrant.dimensions` | `${powerrag.embedding.dimensions}` | Must match embedding output |
| `spring.data.redis.host` | `localhost` | Redis host |
| `powerrag.jwt.expiration-ms` | `86400000` | JWT TTL (24 h) |
| `powerrag.ingestion.chunk-size` | `512` | Chunk size in words |
| `powerrag.ingestion.chunk-overlap` | `64` | Overlap between chunks |
| `powerrag.rag.top-k` | `10` | Documents to retrieve |
| `powerrag.rag.max-context-chars` | `24000` | Max chars sent to LLM |
| `powerrag.models.cache.similarity-threshold` | `0.92` | Semantic cache cosine threshold |
| `powerrag.models.cache.ttl-seconds` | `86400` | Cache TTL |
| `powerrag.upload.storage-path` | `./uploads` | Document file storage |
| `powerrag.mcp.rag-enabled` | `false` | Attach MCP tools to chat calls (requires `spring.ai.mcp.client.enabled=true` via dev profile) |
| `powerrag.rag.intent-routing-enabled` | `true` | Fast LLM classifier to decide per-query whether to retrieve KB and/or use MCP tools |

### Environment Variables

| Variable | Required | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | Yes (for Claude) | Anthropic API key |
| `GOOGLE_API_KEY` | Yes (Gemini chat, **embeddings**, **guardrails**, Imagen) | Google AI Studio API key |
| `POWERRAG_EMBEDDING_DIMENSIONS` | No | Override embedding/Qdrant vector size (default `768` for local stack) |
| `POWERRAG_GUARDRAIL_MODEL` | No | Override input guard model (default `gemini-2.5-flash`) |
| `JWT_SECRET` | Yes | Min 32-char JWT signing secret |
| `PG_USER` | No (default: `powerrag`) | PostgreSQL username |
| `PG_PASSWORD` | No (default: `powerrag_secret`) | PostgreSQL password |
| `SPRING_DATASOURCE_URL` | No | Override PostgreSQL JDBC URL |
| `SPRING_DATA_REDIS_HOST` | No | Override Redis host |
| `QDRANT_HOST` | No | Override Qdrant host |
| `OLLAMA_BASE_URL` | No | Override Ollama URL |
| `UPLOAD_STORAGE_PATH` | No | Override file upload directory |

---

## API Reference

All endpoints are prefixed with `/api`. Authentication uses `Authorization: Bearer <jwt>`.

### Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | None | Login → returns JWT |
| `POST` | `/api/auth/register` | None | Register new user |

### Chat / RAG

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/chat/query` | USER | Health probe |
| `POST` | `/api/chat/query` | USER | Full RAG query (supports images, MCP tools, intent routing) |
| `GET` | `/api/chat/mcp-tools` | USER | List available MCP tools and capability flags |

**Request body (`POST /api/chat/query`):**
```json
{
  "question": "What is the approval rate for STEM grants?",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "language": "en",
  "modelProvider": "GEMINI",
  "modelId": "gemini-2.5-pro",
  "imageBase64": "data:image/png;base64,..."
}
```

**Response:**
```json
{
  "answer": "Based on the grant data...",
  "confidence": 0.87,
  "sources": [{ "fileName": "report.pdf", "section": "page-3", "snippet": "..." }],
  "modelId": "gemini-2.5-pro",
  "durationMs": 1234,
  "interactionId": "uuid",
  "cacheHit": false,
  "error": null,
  "generatedImageBase64": null,
  "mcpInvocations": [
    { "serverId": "powerrag-tools", "toolName": "jira_search_issues", "success": true, "durationMs": 843 }
  ]
}
```

### Documents

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/documents/upload` | USER | Upload and ingest a document |
| `GET` | `/api/documents` | USER | List all ingested documents |
| `DELETE` | `/api/documents/{id}` | USER | Delete document and its vectors |
| `GET` | `/api/documents/{id}/file` | USER | Download original file |

### Text-to-SQL

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/sql/query` | USER | Natural language → SQL → results |
| `GET` | `/api/sql/schema` | ADMIN | View current schema description |
| `POST` | `/api/sql/refresh-schema` | ADMIN | Reload schema from database |

**Request body (`POST /api/sql/query`):**
```json
{
  "question": "How many grant applications are pending review?"
}
```

### Admin

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/admin/interactions` | ADMIN | Paginated interaction history |
| `GET` | `/api/admin/guardrail-flags` | ADMIN | Guardrail flag log |
| `GET` | `/api/admin/stats` | ADMIN | Usage statistics |

### Feedback

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/feedback` | USER | Submit thumbs/star rating |
| `GET` | `/api/feedback/{interactionId}` | USER | Get feedback for interaction |

### Ollama

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/ollama/models` | USER | List locally available Ollama models |

---

## Database Schema

The schema is managed by Flyway and applied automatically on startup.

| Migration | Description |
|---|---|
| `V1__init_schema.sql` | `users`, `user_roles`, `interactions`, `feedback`, `guardrail_flags` — seeds default admin |
| `V2__document_ingestion.sql` | `documents`, `document_chunks` (with GIN full-text index) |
| `V3__rag_core.sql` | `top_chunk_ids` column on `interactions` |
| `V4__fix_confidence_column_type.sql` | Widens `confidence` column precision |
| `V5__feedback.sql` | Feedback schema refinements |
| `V6__document_storage_path.sql` | `storage_path` column on `documents` |
| `V7__grants_schema.sql` | Sample grants domain: `grant_programs`, `grant_applicants`, `grant_applications`, `grant_reviewers`, `grant_reviews`, `grant_disbursements` + seed data |
| `V8__interaction_mcp_invocations.sql` | Adds `mcp_invocations jsonb` column to `interactions` (nullable; stores tool timing + outcome per chat turn) |

---

## LLM Providers

### Supported Models

| Provider | Model ID | Role | Notes |
|---|---|---|---|
| Anthropic | `claude-opus-4-6` | Powerful reasoning | Best for complex analysis |
| Anthropic | `claude-sonnet-4-6` | Balanced ⭐ default | Best price/performance |
| Anthropic | `claude-haiku-4-5-20251001` | Fast | Low-latency Claude option |
| Google | `gemini-2.5-pro` | Powerful | Default for Text-to-SQL |
| Google | `gemini-2.5-flash` | Balanced | Good for most queries |
| Google | `gemini-2.5-flash-lite` | Fast | Low-latency queries |
| Ollama | `qwen2.5-coder:7b` | Code analysis | Local, no API key needed |
| Ollama | `deepseek-coder-v2:16b` | Code Q&A | Local |
| Google | `gemini-embedding-001` | **Embeddings** | Qdrant + semantic cache (768-dim); needs `GOOGLE_API_KEY` |
| Google | `gemini-2.5-flash` | **Input guardrails** | Fast safety classification; configurable via `powerrag.guardrails` |
| Google | `imagen-3.0-generate-002` | Image generation | Requires API key |
| Google | `gemini-2.0-flash-preview-image-generation` | Image generation | Fallback |

### Model Routing

The frontend `ModelSelector` lets the user pick any provider/model per conversation. The backend resolves the correct `ChatClient` bean and overrides the model at request time for dynamic routing:

- **Anthropic** — separate bean per model
- **Gemini** — single base bean; model overridden via `GoogleGenAiChatOptions`
- **Ollama** — single base bean; model overridden via `OllamaChatOptions`

---

## Security

### Authentication

- JWT tokens are issued on login (`POST /api/auth/login`) and expire after 24 hours.
- Tokens are stored in `localStorage` and sent as `Authorization: Bearer <token>`.
- The frontend clears expired tokens on the login page mount to prevent 401 redirect loops.

### Authorisation

| Role | Permissions |
|---|---|
| `USER` | Chat, document upload/delete, feedback, preferences |
| `ADMIN` | All USER permissions + admin dashboard, schema refresh, interaction log |

### Guardrails

Every query passes through two guardrail stages:

1. **Input guardrail** — **Gemini 2.5 Flash** (`gemini-2.5-flash`, Google’s fast 2.5-tier model) classifies the user message; harmful prompts are blocked before retrieval or the main LLM call. On API errors the service **fails open** (allows the request).
2. **Output guardrail** — regex-based PII detection and redaction on the model response (no extra LLM call).

Blocked interactions are logged to `guardrail_flags` with the triggering category.

### CORS

The backend allows all `localhost` origins (any port) during development:
```
http://localhost:[*]
https://localhost:[*]
```
Restrict this to specific origins in production via `SecurityConfig.java`.

---

## Testing

### Backend

```bash
cd backend

# Unit tests only (fast, no Docker required)
./mvnw test -Dtest="*Test" -DfailIfNoTests=false

# Full suite including BDD + integration (requires Docker for Testcontainers)
./mvnw verify

# Coverage report (HTML at target/site/jacoco/index.html)
./mvnw verify jacoco:report
```

Coverage targets: **≥80% line coverage**, **≥75% branch coverage** (enforced by JaCoCo).

**Test categories:**

| Category | Location | Tool |
|---|---|---|
| Unit tests | `src/test/java/com/powerrag/**/*Test.java` | JUnit 5 + Mockito |
| Integration tests | `src/test/java/com/powerrag/**/*IntegrationTest.java` | Testcontainers |
| BDD scenarios | `src/test/resources/features/*.feature` | Cucumber 7 |

Key test files:
- `RagServiceTest` — core RAG pipeline (happy path, cache, guardrails, LLM failure)
- `HybridRetrieverTest` — RRF merge logic
- `SemanticCacheIntegrationTest` — Redis cache round-trips
- `GuardrailsIntegrationTest` — guardrail pipeline with stubbed Gemini calls
- `TextToSqlServiceTest` / `TextToSqlIntegrationTest` — SQL generation
- `DocumentIngestionIntegrationTest` — end-to-end ingest → retrieve

### Frontend

```bash
cd frontend
npm install

# Unit tests (Vitest)
npm test

# Watch mode
npm run test:watch

# Coverage report
npm run test:coverage

# E2E tests (Playwright — requires running app)
npm run test:e2e
```

---

## Deployment

### Production Checklist

- [ ] Generate strong `JWT_SECRET` (≥32 characters, random)
- [ ] Replace self-signed SSL certificates with CA-signed certs
- [ ] Change default `admin` password (update BCrypt hash in V1 migration or via API)
- [ ] Set `CORS` origins to your actual domain in `SecurityConfig.java`
- [ ] Set `powerrag.upload.storage-path` to a persistent volume path
- [ ] Configure Qdrant with authentication if exposed publicly
- [ ] Set Redis `requirepass` for production
- [ ] Review `powerrag.text-to-sql.allowed-tables` — restrict to tables users should query
- [ ] Set `spring.ai.retry.max-attempts` to a reasonable value (currently `1` for fast-fail)

### Docker Production Build

```bash
# Build backend image
docker build -t powerrag-backend:latest ./backend

# Build frontend image
docker build -t powerrag-frontend:latest ./frontend

# Or build everything with compose
docker compose build
docker compose up -d
```

### GPU Support (Ollama)

Uncomment the GPU section in `docker-compose.yml` for NVIDIA GPU acceleration:

```yaml
deploy:
  resources:
    reservations:
      devices:
        - driver: nvidia
          count: all
          capabilities: [gpu]
```

---

## Training Materials

A self-contained HTML course — **Spring AI & RAG Development** — is included in the [`training/`](training/) directory. It is aimed at junior Java developers and uses code snippets from this project to explain every concept hands-on.

### How to open

```bash
# Open locally in your browser — no server required
open training/index.html          # macOS
start training/index.html         # Windows
xdg-open training/index.html      # Linux
```

Or open the rendered course directly in your browser (opens in a new tab):

<a href="https://lcheeyon.github.io/power-rag/training/index.html" target="_blank"><strong>🎓 Open Training Materials ↗</strong></a>

### Course Structure

| Module | Topics | Focus |
|---|---|---|
| 1 — Foundations | 01–03 | What RAG is, Spring AI abstractions, project setup |
| 2 — LLM Integration | 04–07 | ChatClient, multiple providers, dynamic routing, prompts |
| 3 — Document Ingestion | 08–10 | Parsers, chunking strategies, embeddings |
| 4 — Retrieval | 11–16 | Vector stores, similarity search, FTS, RRF, context assembly |
| 5 — The Full Pipeline | 17–19 | End-to-end RAG flow, semantic caching, guardrails |
| 6 — Advanced Features | 20–22 | Multimodal input, image generation, Text-to-SQL |
| 7 — Production Concerns | 23–24 | JWT security, Flyway migrations |
| 8 — Quality & Ops | 25–26 | Testing Spring AI apps, observability and logging |
| 9 — Local AI Infrastructure | 27 | Open-source model selection, hardware requirements, Ollama configs |
| 10 — Agentic RAG & Live Data | 28–31 | Model Context Protocol, MCP tool servers, intent routing, tool observability |

Each topic page includes:
- Concept explanation with diagrams
- Code snippets with direct links to the source file on GitHub
- Highlighted callout boxes (concept, tip, warning)
- Previous / Next navigation

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes and add tests (coverage must not drop below thresholds)
4. Run the full test suite: `cd backend && ./mvnw verify`
5. Run frontend tests: `cd frontend && npm test`
6. Submit a pull request

### Code Style

- Backend: standard Java conventions, Lombok for boilerplate, no raw SQL outside of Flyway migrations and repository query methods
- Frontend: TypeScript strict mode, functional components, TanStack Query for server state
- All new features must include unit tests; integration tests for data-layer changes

---

## Licence

MIT — see [LICENSE](LICENSE) for details.
