# Architecture Deep-Dive

This document describes the internal design of the Power RAG system in detail.

---

## RAG Pipeline

The core query path is orchestrated by `RagService.query()` and consists of nine sequential stages:

```
User Query
    │
    ▼  Stage 0 ─────────────────────────────────────────────────
    │  Input Guardrail (GuardrailService)
    │  • Sends query to llama-guard3:8b via Ollama
    │  • BLOCK → returns 403-equivalent response + logs to guardrail_flags
    │  • PASS  → continues
    │
    ▼  Stage 1 ─────────────────────────────────────────────────
    │  Semantic Cache Lookup (SemanticCache → Redis)
    │  • Embeds query with nomic-embed-text
    │  • Performs Redis vector search (cosine similarity ≥ 0.92)
    │  • HIT  → returns cached answer immediately (saves ~2–10 s LLM call)
    │  • MISS → continues
    │
    ▼  Stage 1.5 ───────────────────────────────────────────────
    │  Image Generation Intent Detection (ImageGenerationService)
    │  • Checks for verb ("generate","draw"…) + noun ("image","picture"…)
    │  • If detected: calls Imagen 3 → falls back to Gemini Flash image-gen
    │  • Returns generated image as data-URL in RagResponse.generatedImageBase64
    │
    ▼  Stage 2 ─────────────────────────────────────────────────
    │  Hybrid Retrieval (HybridRetriever)
    │  • Dense path:   Qdrant.similaritySearch (top-K×2 vectors)
    │  • Keyword path: PostgreSQL FTS fullTextSearch (top-K×2 chunks)
    │  • Merge:        Reciprocal Rank Fusion (k=60)  →  top-K results
    │
    ▼  Stage 3 ─────────────────────────────────────────────────
    │  Confidence Scoring (ConfidenceScorer)
    │  • Weighted average of RRF scores
    │  • score < 0.1  → hasRelevantDocs = false  → LLM uses general knowledge
    │  • score ≥ 0.1  → context assembled from retrieved chunks
    │
    ▼  Stage 4 ─────────────────────────────────────────────────
    │  Context Assembly (ContextAssembler)
    │  • Formats chunks as "[SOURCE 1] {file} § {section}\n{text}\n"
    │  • Caps total context at 24 000 chars
    │  • Extracts SourceRef list (fileName, section, snippet, page/row number)
    │
    ▼  Stage 5 ─────────────────────────────────────────────────
    │  LLM Call (RagService.callLlm)
    │  • Builds prompt via MultilingualPromptBuilder
    │    – Adds "An image has been attached…" if imageBase64 present
    │    – Instructs LLM to cite [SOURCE N] inline
    │    – Appends language instruction (respond in EN / 简体中文 / …)
    │  • Routes to correct ChatClient (Anthropic / Gemini / Ollama)
    │  • Overrides model at request time for Gemini and Ollama
    │  • Sends UserMessage with Media for multimodal queries
    │
    ▼  Stage 6 ─────────────────────────────────────────────────
    │  Output Guardrail (GuardrailService)
    │  • Detects PII in LLM response
    │  • WARN → redacts PII + logs to guardrail_flags; returns redacted text
    │  • PASS → returns raw answer
    │
    ▼  Stage 7 ─────────────────────────────────────────────────
    │  Cache Store (SemanticCache)
    │  • Stores (query embedding, answer, confidence, sources, modelId) in Redis
    │  • TTL: 24 h
    │
    ▼  Stage 8 ─────────────────────────────────────────────────
    ▼  Audit Log (InteractionRepository)
       • Persists full interaction to PostgreSQL interactions table
       • Includes top_chunk_ids for retrievability analysis
```

---

## Hybrid Retrieval

The `HybridRetriever` combines two independent search paths and merges them with **Reciprocal Rank Fusion (RRF)**:

```
Query
  ├─► Dense Search (Qdrant)
  │     nomic-embed-text embeds query (768-dim)
  │     Qdrant cosine similarity search → top-2K results
  │     Each result ranked by position: score += 1 / (rank + 1 + 60)
  │
  └─► Keyword Search (PostgreSQL)
        Full-text search on document_chunks.chunk_text
        Using tsvector GIN index → top-2K results
        Each result ranked by position: score += 1 / (rank + 1 + 60)
          │
          ▼
      RRF Merge → sort by combined score → top-K (default: 10)
```

RRF constant `k=60` prevents high-rank results from dominating and ensures both signals contribute.

---

## Document Ingestion Pipeline

```
File Upload (POST /api/documents/upload)
    │
    ▼
DocumentIngestionService.ingest()
    │
    ├─► DocumentParser (by extension)
    │     PDF  → PDFBox text extractor, per-page sections
    │     DOCX → Apache POI, per-paragraph sections
    │     XLSX → Apache POI, per-row sections (metadata: row_number, sheet_name)
    │     PPTX → Apache POI, per-slide sections
    │     .java → JavaParser AST, per-method/class sections
    │     Image → multimodal LLM description (Claude/Gemini) as text
    │
    ├─► SlidingWindowChunkingStrategy
    │     Splits each section into word-based windows
    │     Chunk size: 512 words, Overlap: 64 words
    │     Builds word→line map for start_line metadata
    │     Preserves all section metadata on each chunk
    │
    ├─► VectorStore.add() (Qdrant via Spring AI)
    │     Embeds chunk text with nomic-embed-text
    │     Stores vector + metadata payload in Qdrant
    │
    └─► DocumentChunkRepository.saveAll() (PostgreSQL)
          Stores chunk text + metadata for FTS
          GIN index on chunk_text tsvector column
```

---

## Semantic Cache

The semantic cache avoids duplicate LLM calls for semantically equivalent queries:

```
lookup(question, lang):
    1. Embed question with nomic-embed-text (768-dim)
    2. Redis VSEARCH on "powerrag:cache:{lang}" index
    3. Find nearest neighbour with cosine score ≥ 0.92
    4. HIT: deserialise CacheHit (answer, confidence, sources, modelId)
    5. MISS: return empty Optional

store(question, lang, answer, ...):
    1. Embed question
    2. Serialise CacheHit as Redis hash
    3. Store vector + hash with 24 h TTL
    4. Key: "powerrag:cache:{lang}:{uuid}"
```

The `NoOpSemanticCacheService` (used in tests) is a no-op that always returns a miss, avoiding Redis dependency in unit tests.

---

## Text-to-SQL Pipeline

```
POST /api/sql/query  {"question": "..."}
    │
    ├─► SchemaIntrospector (cached on startup, refreshable)
    │     Queries information_schema for allowed tables
    │     Adds enum hints: queries DISTINCT values for status/type columns
    │     Returns a natural-language schema description
    │
    ├─► TextToSqlService.generateSql()
    │     System prompt: schema description + SQL rules
    │       (UPPERCASE enum values, ILIKE for text matching, read-only)
    │     LLM: Gemini 2.5 Pro
    │     Response: raw SQL string
    │
    ├─► SqlValidator.validate()
    │     Parses SQL with JSqlParser
    │     Rejects: non-SELECT statements, disallowed tables, subquery attacks
    │
    └─► JDBC execute + results → SqlQueryResponse (rows as List<Map>)
```

Allowed tables are configured in `application.yml`:
```yaml
powerrag:
  text-to-sql:
    allowed-tables: documents,interactions,grant_programs,...
```

---

## LLM Provider Architecture

All LLM calls go through Spring AI's `ChatClient` abstraction. Providers are wired as separate beans in `SpringAiConfig`:

```
SpringAiConfig
├─ claudeSonnet  → AnthropicChatModel (claude-sonnet-4-6)  [@Primary]
├─ claudeOpus    → AnthropicChatModel (claude-opus-4-6)
├─ claudeHaiku   → AnthropicChatModel (claude-haiku-4-5...)
├─ geminiFlash   → GoogleGenAiChatModel (base, model overridden per-request)
├─ geminiPro     → GoogleGenAiChatModel (base, model overridden per-request)
├─ ollamaQwen    → OllamaChatModel (model overridden per-request)
└─ ollamaLlamaGuard → OllamaChatModel (no system prompt, for guardrails)
```

**Dynamic routing** in `RagService.resolveClient()` + `callLlm()`:
- Anthropic: resolved by `clientsByKey` map lookup (separate bean per model)
- Gemini: always uses `geminiBaseClient`; model overridden via `GoogleGenAiChatOptions.builder().model(id)`
- Ollama: always uses `ollamaBaseClient`; model overridden via `OllamaChatOptions.builder().model(id)`

---

## Security Architecture

```
Request
    │
    ▼
JwtAuthFilter (OncePerRequestFilter)
    │  Extracts Bearer token from Authorization header
    │  Validates JWT signature (HMAC-SHA256, configurable secret)
    │  Sets SecurityContext with UserDetails
    │
    ▼
SecurityConfig (FilterChain)
    │  Public: /api/auth/**, /actuator/health
    │  USER:   /api/chat/**, /api/documents/**, /api/feedback/**, ...
    │  ADMIN:  /api/admin/**, /api/sql/refresh-schema, /api/sql/schema
    │
    ▼
Controller → Service → Repository
```

JWT tokens are stateless — no server-side session storage. Token expiry is 24 hours (configurable).

---

## Multimodal Image Flow

### Image Interpretation (user pastes image)

```
ChatWindow (React)
    │  onPaste → reads clipboard image → stores as dataURL in state
    │  Displays thumbnail preview above input
    │  Sends {question, imageBase64: "data:image/png;base64,..."} to API
    │
    ▼
RagService.callLlm()
    │  Detects imageBase64 present
    │  MultilingualPromptBuilder prepends: "An image has been attached..."
    │  Builds UserMessage with Media (org.springframework.ai.content.Media)
    │  Passes to LLM via ChatClient.ChatClientRequestSpec.messages(userMessage)
    │  GoogleGenAiChatModel.mediaToParts() converts Media → Gemini Part.inlineData
    │  AnthropicChatModel converts Media → Anthropic vision content block
```

### Image Generation (user requests "draw an image of X")

```
RagService.query()
    │  imageGenerationService.isImageGenerationRequest(question) → true
    │
    ▼
ImageGenerationService.generateImage(prompt)
    ├─► tryImagen(): com.google.genai.Client.models.generateImages()
    │     model: imagen-3.0-generate-002
    │     Returns GenerateImagesResponse → GeneratedImage → Image → byte[]
    │
    └─► tryGeminiImageOut() [fallback]:
          model: gemini-2.0-flash-preview-image-generation
          GenerateContentConfig.responseModalities("IMAGE", "TEXT")
          Returns GenerateContentResponse → Part.inlineData → Blob.data → byte[]
    │
    ▼
RagResponse.generatedImageBase64 = "data:image/png;base64,..."
    │
    ▼
ChatWindow (React)
    Shows <img src={generatedImageBase64}> in assistant message bubble
```

---

## Frontend Architecture

```
App.tsx
├─ AuthContext (JWT state, login/logout)
├─ React Router
│   ├─ /login      → LoginPage
│   ├─ /chat       → ChatPage (protected)
│   │   ├─ Left sidebar:  Knowledge Base (UploadZone + document list with delete)
│   │   ├─ Main area:     ChatWindow (messages + image paste + model selector)
│   │   └─ Right sidebar: Sources (RAG citations + document list)
│   ├─ /sql        → SqlPage (Text-to-SQL interface)
│   └─ /admin      → AdminPage (protected, ADMIN role)
│       └─ AdminDashboard (interactions, flags, stats)
└─ i18n (en / zh-CN / zh-TW)
```

**State management:**
- Server state: TanStack Query (`useQuery`, `useMutation`) with automatic cache invalidation
- Auth state: React Context + `localStorage` for JWT
- UI state: `useState` / `useRef` within components

**API layer (`src/api/`):**
- `client.ts` — Axios instance with base URL + JWT interceptor + 401 redirect
- `authApi.ts` — login, register
- `chatApi.ts` — RAG query (supports imageBase64, returns generatedImageBase64)
- `documentApi.ts` — upload, list, delete
- `sqlApi.ts` — natural language SQL query
- `adminApi.ts` — interactions, stats, guardrail flags
- `modelApi.ts` — Ollama model list

---

## Database Entity Relationships

```
users
  ├─< user_roles          (many roles per user)
  ├─< interactions        (query/response audit log)
  │     └─< feedback      (thumbs/star per interaction)
  │     └─< guardrail_flags (safety events per interaction)
  └─< documents           (ingested files)
        └─< document_chunks (text chunks for FTS + vector metadata)

-- Grants domain (V7, independent)
grant_programs
  └─< grant_applications
        ├─< grant_applicants (referenced)
        ├─< grant_reviews     (one per reviewer)
        └─< grant_disbursements
```
