# Power RAG — Phase Development Plan & Architecture

> Last updated: 2026-03-12
> Stack: Java 25 · Spring Boot 3.5.11 · Spring AI 1.1.2 · React 18 · PostgreSQL · Qdrant · Redis Stack

---

## System Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                          React 18 Frontend                             │
│  shadcn/ui · TailwindCSS v4 · Framer Motion · i18next (EN / ZH-CN)    │
│  ChatWindow · UploadZone · ModelSelector · AdminDashboard              │
└───────────────────────────────┬────────────────────────────────────────┘
                                │ REST / JWT
┌───────────────────────────────▼────────────────────────────────────────┐
│                     Spring Boot 3.5.11 Backend                         │
│                                                                        │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │ Auth / JWT  │  │  Ingestion   │  │  RAG Core   │  │   Admin     │ │
│  │ (Phase 1)   │  │  (Phase 2)   │  │  (Phase 3)  │  │  (Phase 8)  │ │
│  └─────────────┘  └──────────────┘  └─────────────┘  └─────────────┘ │
│                                                                        │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │  Semantic   │  │  Text-to-SQL │  │  Multilang  │  │  Guardrails │ │
│  │  Cache      │  │  (Phase 5)   │  │  (Phase 6)  │  │  (Phase 7)  │ │
│  │  (Phase 4)  │  └──────────────┘  └─────────────┘  └─────────────┘ │
│  └─────────────┘                                                       │
│                                                                        │
│  Spring AI 1.1.2 ── ChatClient beans (Claude Sonnet ★, Gemini Flash,  │
│                      Ollama Qwen/DeepSeek/LlamaGuard)                  │
└────┬────────────────────┬─────────────────────┬────────────────────────┘
     │                    │                     │
┌────▼───┐  ┌─────────────▼──┐  ┌──────────────▼─────┐
│Postgres│  │    Qdrant       │  │   Redis Stack 7.x  │
│(JPA /  │  │  (Vector Store  │  │  (Semantic Cache   │
│Flyway) │  │  knowledge base)│  │   threshold 0.92)  │
└────────┘  └────────────────┘  └────────────────────┘
     │
┌────▼────────────────────┐
│  Ollama (local)         │
│  nomic-embed-text       │  ← embeddings
│  llama-guard3:8b        │  ← guardrails
│  qwen2.5-coder:32b      │  ← code Q&A
└─────────────────────────┘
```

---

## Phase Roadmap

| Phase | Title                       | Status      | Key Deliverable                                      |
|-------|-----------------------------|-------------|------------------------------------------------------|
| 1     | Infrastructure Setup        | ✅ Complete | Spring context, JWT auth, DB/Redis/Qdrant wired, BDD |
| 2     | Document Ingestion Pipeline | 🔄 Active   | Parsers (Java/PDF/Excel/Word), chunking, Qdrant store|
| 3     | RAG Core + Source Citations | ⏳ Pending  | HybridRetriever, ConfidenceScorer, ContextAssembler  |
| 4     | Semantic Cache              | ⏳ Pending  | Redis vector cache, TTL, language-aware keying        |
| 5     | Text-to-SQL                 | ⏳ Pending  | SchemaIntrospector, SqlValidator, PostgreSQL executor |
| 6     | Multilingual Support        | ⏳ Pending  | ZH-CN / EN prompt routing, language detection        |
| 7     | Guardrails                  | ⏳ Pending  | LlamaGuard3 input/output filter, PII redaction       |
| 8     | Interaction Audit + Feedback| ⏳ Pending  | AuditService, FeedbackService, admin dashboard API   |
| 9     | Frontend Development        | ⏳ Pending  | ChatWindow, UploadZone, AdminDashboard, E2E flows     |
| 10    | Testing & Hardening         | ⏳ Pending  | JMeter load test, ZAP scan, multilingual eval        |
| 11    | Deployment                  | ⏳ Pending  | Docker Compose prod config, smoke tests              |

---

## Phase 2 — Document Ingestion Pipeline

### Goal
Accept file uploads (.java, .pdf, .xlsx, .docx), parse each format into text chunks with
rich metadata, embed via `nomic-embed-text`, and store in Qdrant for later RAG retrieval.

### Architecture

```
POST /api/documents/upload (multipart/form-data)
         │
         ▼
DocumentController
         │ detects MIME / extension
         ▼
DocumentIngestionService
         │
    ┌────┴──────────────────┐
    │    Parser Registry    │
    │  ┌────────────────┐   │
    │  │JavaSourceParser│   │  → chunks with class/method/line metadata
    │  ├────────────────┤   │
    │  │  PdfParser     │   │  → chunks with page_number metadata
    │  ├────────────────┤   │
    │  │  ExcelParser   │   │  → chunks with sheet_name / row metadata
    │  ├────────────────┤   │
    │  │  WordParser    │   │  → chunks with heading hierarchy metadata
    │  └────────────────┘   │
    └──────────┬────────────┘
               │ raw text + metadata per section
               ▼
    SlidingWindowChunkingStrategy
    (configurable size + overlap)
               │ List<Chunk>
               ▼
    Spring AI VectorStore (Qdrant)
    .add(List<Document>)         ← metadata stored alongside embeddings
               │
               ▼
    PostgreSQL documents table   ← ingestion audit log
```

### New Packages

```
com.powerrag/
├── domain/
│   ├── Document.java                 ← JPA entity (metadata, status, user)
│   ├── DocumentRepository.java
│   ├── DocumentChunk.java            ← JPA entity (qdrant_id, metadata JSON)
│   └── DocumentChunkRepository.java
│
├── ingestion/
│   ├── model/
│   │   ├── ParsedDocument.java       ← DTO: sections with raw text + metadata
│   │   └── Chunk.java               ← DTO: text + metadata + doc_type
│   │
│   ├── parser/
│   │   ├── DocumentParser.java       ← interface
│   │   ├── JavaSourceParser.java     ← apache javaparser or regex-based
│   │   ├── PdfParser.java            ← Apache PDFBox
│   │   ├── ExcelParser.java          ← Apache POI XSSF
│   │   └── WordParser.java           ← Apache POI XWPF
│   │
│   ├── chunking/
│   │   ├── ChunkingStrategy.java     ← interface
│   │   └── SlidingWindowChunkingStrategy.java
│   │
│   ├── service/
│   │   └── DocumentIngestionService.java  ← orchestrator
│   │
│   └── exception/
│       └── UnsupportedDocumentTypeException.java
│
└── api/
    └── DocumentController.java       ← POST /api/documents/upload
                                         GET  /api/documents (list)
                                         DELETE /api/documents/{id}
```

### API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/documents/upload` | USER | Upload and ingest a document |
| `GET`  | `/api/documents` | USER | List uploaded documents |
| `DELETE` | `/api/documents/{id}` | ADMIN | Remove document and its chunks |

**Upload request** (`multipart/form-data`):
```
file: <binary>
description: "optional human-readable description"
```

**Upload response** (200 OK):
```json
{
  "documentId": "uuid",
  "fileName": "architecture.pdf",
  "chunkCount": 42,
  "status": "INDEXED",
  "uploadedAt": "2026-03-12T06:00:00Z"
}
```

### Database Schema (V2 migration)

```sql
CREATE TABLE documents (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name    VARCHAR(512) NOT NULL,
    file_type    VARCHAR(20)  NOT NULL,   -- JAVA, PDF, EXCEL, WORD
    file_size    BIGINT       NOT NULL,
    description  TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, INDEXED, FAILED
    chunk_count  INTEGER,
    user_id      UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE document_chunks (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    qdrant_id    VARCHAR(64) NOT NULL,
    chunk_index  INTEGER     NOT NULL,
    chunk_text   TEXT        NOT NULL,
    metadata     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Metadata Schema per Doc Type

| Field | Java | PDF | Excel | Word |
|-------|------|-----|-------|------|
| `file_name` | ✓ | ✓ | ✓ | ✓ |
| `doc_type` | `JAVA` | `PDF` | `EXCEL` | `WORD` |
| `class_name` | ✓ | — | — | — |
| `method_name` | ✓ | — | — | — |
| `page_number` | — | ✓ | — | — |
| `sheet_name` | — | — | ✓ | — |
| `row_number` | — | — | ✓ | — |
| `heading` | — | — | — | ✓ |
| `section` | ✓ | ✓ | ✓ | ✓ |

### New Maven Dependencies

```xml
<!-- PDF parsing -->
<dependency>
  <groupId>org.apache.pdfbox</groupId>
  <artifactId>pdfbox</artifactId>
  <version>3.0.3</version>
</dependency>

<!-- Excel + Word parsing -->
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.3.0</version>
</dependency>

<!-- Java source parsing -->
<dependency>
  <groupId>com.github.javaparser</groupId>
  <artifactId>javaparser-core</artifactId>
  <version>3.26.1</version>
</dependency>
```

### Chunking Strategy

```
SlidingWindowChunkingStrategy
  chunkSize   = 512 tokens  (configurable via powerrag.ingestion.chunk-size)
  chunkOverlap = 64 tokens  (configurable via powerrag.ingestion.chunk-overlap)

Algorithm:
  1. Split text into sentences/lines
  2. Accumulate until chunkSize reached
  3. Slide forward by (chunkSize - chunkOverlap) tokens
  4. Attach metadata from parent section to each chunk
```

### Phase 2 Test Plan

#### Unit Tests
| Class | Test File | Scenarios |
|-------|-----------|-----------|
| `JavaSourceParser` | `JavaSourceParserTest` | Parse sample .java; verify class/method metadata; chunk count ≥ 1 |
| `PdfParser` | `PdfParserTest` | Parse sample .pdf; every chunk has page_number; text is non-empty |
| `ExcelParser` | `ExcelParserTest` | Parse sample .xlsx; sheet_name and row_number present; multi-sheet |
| `WordParser` | `WordParserTest` | Parse sample .docx; heading hierarchy preserved; section metadata set |
| `SlidingWindowChunkingStrategy` | `ChunkingStrategyTest` | Correct chunk count for given size; overlap preserved; metadata forwarded |
| `DocumentIngestionService` | `DocumentIngestionServiceTest` | Mocked parsers + VectorStore: correct parser dispatched, chunks stored |

#### Integration Test (Testcontainers + Qdrant)
- `DocumentIngestionIntegrationTest`: upload real PDF → `DocumentIngestionService.ingest()` → query Qdrant → at least 1 matching chunk returned with correct metadata

#### BDD Scenarios (`ingestion.feature`)
```gherkin
Feature: Document Ingestion Pipeline
  As a knowledge base administrator
  I want to upload various document types
  So that their content is searchable via RAG

  Scenario: Java file is uploaded and class/method metadata is stored
  Scenario: PDF is uploaded and page_number metadata is present on every chunk
  Scenario: Excel file is uploaded and sheet and row metadata are captured
  Scenario: Word document is uploaded and heading hierarchy is preserved
  Scenario: Unsupported file type is rejected with 400
```

#### Coverage Requirements
- JaCoCo line ≥ 80%, branch ≥ 75% (enforced by `mvn verify`)

---

## Phase 3 — RAG Core + Source Citations

### Goal
Accept a natural-language question, retrieve the most relevant document chunks from
Qdrant (hybrid dense + BM25 + RRF merge), assemble context, call the LLM, and return
an answer with source citations.

### Key Components
- `HybridRetriever` — dense vector search (Qdrant) + BM25 keyword search + RRF merge
- `ConfidenceScorer` — compute confidence from top-k similarity scores
- `ContextAssembler` — format retrieved chunks into the LLM system prompt
- `RagService` — orchestrator (retrieve → assemble → LLM call → extract sources)
- `SourceRef` record — `{fileName, pageNumber, section, snippet}`

### API
- `POST /api/chat/query` — replaces stub; full RAG pipeline

---

## Phase 4 — Semantic Cache

### Goal
Avoid redundant LLM calls by caching answers in Redis Stack's vector store.
On incoming query, compute embedding → cosine similarity vs cached queries →
if similarity ≥ 0.92 return cached answer (with `cacheHit=true`).

### Key Components
- `SemanticCacheService` — `lookup(query)` / `store(query, answer)`
- Language-aware key prefix: `{lang}:{embedding}`
- TTL: 86400s (24h)

---

## Phase 5 — Text-to-SQL

### Goal
Convert natural-language questions about structured data into parameterized SQL
SELECT queries, execute against PostgreSQL, and return formatted results.

### Key Components
- `SchemaIntrospector` — reads table/column metadata from DB at startup
- `SqlValidator` — rejects all non-SELECT statements (INSERT/UPDATE/DELETE/DDL)
- `TextToSqlService` — LLM prompt → SQL → validate → execute → format

---

## Phase 6 — Multilingual Support

### Goal
Route queries and responses in the user's preferred language (EN / ZH-CN).
All prompts, cache keys, and audit records are language-tagged.

### Key Components
- `LanguageDetector` — detect query language
- Language-aware system prompt builder
- `UserPreferenceService` — persist preferred_language per user

---

## Phase 7 — Guardrails

### Goal
Filter discriminatory / biased inputs and outputs via `llama-guard3:8b`.
Block harmful inputs before LLM call; intercept biased outputs before delivery.
All violations written to `guardrail_flags` table.

### Key Components
- `InputGuardrailAdvisor` — Spring AI `ChatClient.Advisor`
- `OutputGuardrailAdvisor` — Spring AI `ChatClient.Advisor`
- `GuardrailFlagRepository` — persistence

---

## Phase 8 — Interaction Audit + Feedback

### Goal
Persist every interaction (query, response, model, confidence, sources, cache_hit)
and allow users to submit thumb/star ratings with optional comments.

### Key Components
- `AuditService` — writes `interactions` table after every chat call
- `FeedbackService` — validates and stores user ratings
- `AdminController` — paginated `GET /api/admin/interactions`

---

## Phase 9 — Frontend Development

### Key UI Components
- `ChatWindow` — streaming token display, source citations panel
- `UploadZone` — drag-and-drop with file type validation
- `ModelSelector` — dropdown for LLM provider/model selection
- `ConfidenceBadge` — green/yellow/red based on confidence score
- `AdminDashboard` — interaction table, filter controls, rating charts
- Language toggle — EN ↔ 简体中文 with localStorage persistence

---

## Phase 10 — Testing & Hardening

- JMeter: 20 concurrent users, P95 RAG latency < 3s, cache hit < 200ms
- OWASP ZAP: no HIGH severity findings
- Multilingual eval: 50 EN + 50 ZH-CN questions, recall@5 ≥ 0.85
- Semantic cache threshold sensitivity analysis (0.85–0.95)

---

## Phase 11 — Deployment

- Production Docker Compose with named volumes, health checks
- Nginx reverse proxy for frontend
- Environment-based secret injection
- Smoke tests post-deploy (actuator health, Qdrant/Redis/PG connectivity)

---

## Dependency Versions (confirmed working)

| Library | Version | Notes |
|---------|---------|-------|
| Java | 25 | JDK 25 required |
| Spring Boot | 3.5.11 | Required for Java 25 ASM compat |
| Spring AI | 1.1.2 | Latest GA for Spring Boot 3.x |
| Spring AI 2.0 | 2.0.0-M2 | Milestone, requires Spring Boot 4.x — future |
| Lombok | 1.18.38 | Needs explicit annotationProcessorPaths |
| JaCoCo | 0.8.14 | Required for Java 25 bytecode |
| JJWT | 0.12.6 | JWT auth |
| Testcontainers | 1.20.4 | PostgreSQL + Redis + Qdrant |
| Cucumber | 7.20.1 | BDD |
| Apache PDFBox | 3.0.3 | PDF parsing (Phase 2) |
| Apache POI | 5.3.0 | Excel + Word parsing (Phase 2) |
| JavaParser | 3.26.1 | Java source parsing (Phase 2) |
| React | 18 | Frontend |
| TailwindCSS | v4 | Styling |
| Vitest | latest | Frontend unit tests |
| Playwright | latest | E2E tests |
