# MCP integration — implementation plan

This document describes how to integrate **Model Context Protocol (MCP)** tools into Power RAG’s **existing RAG chat** (`RagService` → `POST /api/chat/query` → `ChatWindow`), with a **clear, consistent UI indicator** whenever MCP tools run.

It assumes **Spring AI MCP** ([MCP overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)) for client wiring, tool discovery, and bridging MCP tools to `ChatClient`.

---

## 1. Goals and non-goals

| Goal | Detail |
|------|--------|
| **G1** | Optional MCP tool use **during the main LLM turn** (same path as today’s `callLlm`), after context is assembled from retrieval when applicable. |
| **G2** | **Structured telemetry** of each tool call (name, server id, success/error, duration, optional redacted args summary) returned on the API and persisted for audit. |
| **G3** | **Visible UI affordance** on assistant messages when any MCP tool was used (badge + detail), mirroring the existing **cache hit** chip pattern in `ChatWindow`. |
| **G4** | **Feature flag + allowlist** so production can disable MCP or restrict servers/tools per environment. |

| Non-goal (initial phase) | Reason |
|---------------------------|--------|
| Exposing Power RAG as an MCP **server** for external clients | Valuable follow-up; keep first delivery focused on **consuming** tools inside chat. |
| Replacing hybrid retrieval with MCP | Retrieval stays primary; tools augment (web, time, memory, etc.). |

---

## 2. Current integration points (codebase)

| Layer | Location | Change |
|-------|----------|--------|
| LLM call | `RagService.callLlm(...)` | Attach Spring AI tool callbacks from MCP; collect invocation records during the chat call. |
| Response DTO | `RagResponse` record | Add field e.g. `List<McpToolInvocationSummary> mcpInvocations` (empty when unused). |
| REST | `ChatController` | No signature change if `RagResponse` carries new data. |
| Persistence | `Interaction` entity | Add JSONB column e.g. `mcp_invocations` for admin/history parity with `sources`. |
| Frontend types | `chatApi.ts` `ChatQueryResponse` | Mirror new field. |
| UI | `ChatWindow.tsx` | Render badge + expandable/tooltip row next to `ConfidenceBadge` / cache chip. |

---

## 3. Architecture (Spring AI first)

### 3.1 Components

1. **`McpClient` configuration** (Boot starter)  
   - Dependency: `spring-ai-starter-mcp-client` (Servlet stack matches existing `spring-boot-starter-web`).  
   - Configure servers via `application.yml`: STDIO commands for dev; **SSE / Streamable HTTP** URLs for staging/prod sidecars.  
   - Verify **Spring AI BOM** version in `pom.xml` includes MCP artifacts; bump `spring-ai.version` if MCP requires a newer release than `1.1.2`.

2. **`McpToolCatalog` / facade** (new package e.g. `com.powerrag.mcp`)  
   - Wraps Spring AI’s registration of MCP tools as **function/tool callbacks** usable by `ChatClient`.  
   - Applies **allowlist** (server id + tool name) from `powerrag.mcp.*` properties.  
   - Injects a **listener** (or uses Spring AI hooks if available) to append to a **request-scoped** `McpInvocationCollector` on each tool execution.

3. **`McpInvocationCollector`** (request-scoped bean or explicit thread-local cleared in `finally`)  
   - Records: `serverId`, `toolName`, `startedAt`, `durationMs`, `ok`, `errorCode/message`, optional `argsSummary` (redacted, max length).  
   - Passed into `RagService.query` / `callLlm` or created at start of `query()` and read after `callLlm` returns.

4. **System prompt addition**  
   - Short instruction: tools are available for **live data / actions**; **KB claims** must still cite `[SOURCE n]` when `hasRelevantDocs` is true.  
   - Keeps RAG behaviour explainable and aligns with guardrails.

### 3.2 Sequence (high level)

```
User message → guardrails → cache? → retrieval + context → callLlm with MCP tools
                                                      ↓
                              collector records each tool call
                                                      ↓
                    answer + mcpInvocations on RagResponse → Interaction save → UI badge
```

### 3.3 When to enable tools

| Scenario | Suggested policy |
|----------|------------------|
| Cache hit | Skip LLM → **no MCP** (consistent with today). |
| Image-only / generation branch | Exclude or use a reduced tool set (config-driven). |
| Text-to-SQL | Out of scope for this endpoint (separate controller). |

---

## 4. API and data model

### 4.1 `McpToolInvocationSummary` (API + JSON)

Serializable DTO (Java record + TypeScript interface):

| Field | Type | Purpose |
|-------|------|---------|
| `serverId` | string | Config id (e.g. `fetch`, `time`). |
| `toolName` | string | MCP tool name. |
| `success` | boolean | Completed without error. |
| `durationMs` | number | Client-side measured round-trip. |
| `errorMessage` | string? | Short, safe message if failed. |
| `argsSummary` | string? | Redacted one-liner (optional, for transparency). |

**Privacy:** Never return raw URLs, tokens, or file paths in full; truncate and redact query parameters.

### 4.2 `RagResponse` extension

```text
List<McpToolInvocationSummary> mcpInvocations   // empty list = no tools used
boolean mcpToolsUsed                              // derived: !mcpInvocations.isEmpty() — optional convenience for UI
```

Frontend: if `mcpToolsUsed` or `mcpInvocations?.length`, show indicator.

### 4.3 Database (`interactions`)

- New Flyway migration: `mcp_invocations jsonb NULL`.  
- Hibernate: `@JdbcTypeCode(SqlTypes.JSON) List<Map<String, Object>>` or a dedicated embeddable list type consistent with `sources`.

---

## 5. UI specification — “clear indicator”

### 5.1 Primary indicator (assistant bubble footer)

Place next to existing **confidence** and **cache hit** chips (`ChatWindow.tsx`):

- **Badge:** e.g. `MCP · 2 tools` or translated `chat.mcpToolsUsed` with count.  
- **Style:** Distinct from cache (e.g. amber/outline `border-amber-600/50 text-amber-400`) so it is visible on dark theme.  
- **`data-testid`:** `mcp-tools-badge` for E2E.

### 5.2 Secondary detail (accessibility + power users)

- **`<details>` / chevron** or **tooltip** listing tool names and duration (e.g. `fetch_url · 340ms`, `get_current_time · 12ms`).  
- On failure: show **warning icon** + short error on the summary line; full text only in expandable section.

### 5.3 i18n

Add keys under `chat.*` in `en.json`, `zh-CN`, `zh-TW`:

- `mcpToolsUsed`, `mcpToolsUsed_one`, `mcpToolsUsed_other` (if using ICU plural)  
- `mcpToolsDetail`, `mcpToolFailed`

### 5.4 Admin dashboard (optional phase 2)

- If interactions table is shown in admin UI, add a column or expandable row for MCP invocation count.

---

## 6. Configuration (`application.yml`)

Suggested namespace:

```yaml
powerrag:
  mcp:
    enabled: false
    # Allow only these logical server ids
    allowed-servers: powerrag-tools
    # Optional: allowed tool names per server (empty = all tools from allowed servers)
    allowed-tools: {}
    request-timeout-ms: 30000
    per-tool-timeout-ms: 15000

spring:
  ai:
    mcp:
      client:
        # STDIO example (dev only)
        stdio:
          connections:
            powerrag-tools:
              command: python3
              args: [ mcp/powerrag_mcp_tools.py ]
        # Or HTTP/SSE endpoints for prod sidecars
```

**Environment-specific:** enable MCP only where the stdio command exists (e.g. `python3 backend/mcp/powerrag_mcp_tools.py` with `pip3 install -r backend/mcp/requirements.txt`) or sidecars exist; production VM should prefer **HTTP transport** over STDIO.

---

## 7. Security and governance

| Topic | Action |
|-------|--------|
| **Auth** | MCP tools run **in the backend** with service identity; no end-user MCP credentials in the browser. |
| **Allowlist** | Default deny; enable named servers/tools only. |
| **Fetch / web** | Restrict schemes (https only), optional domain allowlist, max response bytes. |
| **Filesystem** | If ever enabled, path sandbox + separate role (e.g. ADMIN-only). |
| **Audit** | Log tool invocations server-side (structured JSON); align with `Interaction` retention. |
| **Guardrails** | Run **output guardrail** on final answer after tool loop (already the case in `callLlm`). |

---

## 8. Implementation phases

### Phase 0 — Prerequisites (0.5–1 day)

- [ ] Confirm Spring AI version supports required MCP client starter; update BOM if needed.  
- [ ] Add dependency `spring-ai-starter-mcp-client`.  
- [ ] Document local dev requirement (`pip3 install -r backend/mcp/requirements.txt`, dev profile, or Docker image with Python MCP server).

### Phase 1 — MCP client + collector (1–2 days)

- [ ] Implement `McpToolCatalog` + YAML wiring for **one** reference server (recommend **`@modelcontextprotocol/server-time`** — minimal surface).  
- [ ] Implement `McpInvocationCollector` and wire callbacks so every tool call appends a summary record.  
- [ ] Unit tests with mocked MCP layer or test doubles.

### Phase 2 — RagService integration (1–2 days)

- [ ] Extend `callLlm` to register MCP tools on `ChatClient` request spec when `powerrag.mcp.enabled` and allowlist permits.  
- [ ] Thread collector through `query()`; populate `RagResponse.mcpInvocations`.  
- [ ] Skip tools on cache hit and image-generation short-circuit paths.  
- [ ] Integration test: MCP disabled → empty list; enabled with stub → non-empty list.

### Phase 3 — Persistence + API contract (0.5–1 day)

- [ ] Flyway migration `V8__interaction_mcp_invocations.sql`.  
- [ ] Update `Interaction` builder where saves occur in `RagService`.  
- [ ] Ensure JSON serialisation of `RagResponse` includes new field (Jackson records).

### Phase 4 — Frontend indicator (0.5–1 day)

- [ ] Extend `ChatQueryResponse` and `Message` usage in `ChatWindow`.  
- [ ] Add badge + `<details>` for tool list; styles distinct from cache hit.  
- [ ] i18n strings.  
- [ ] Vitest: render badge when `mcpInvocations.length > 0`.

### Phase 5 — Hardening (1–2 days)

- [ ] Add second server (e.g. **Fetch**) behind feature flag.  
- [ ] Timeouts, circuit breaker, metrics (Micrometer counter per tool).  
- [ ] Prod compose: optional MCP sidecar service + Streamable HTTP URL in config.

### Phase 6 — Follow-ups (backlog)

- [ ] Expose read-only **“search KB”** MCP server using `HybridRetriever` for external agents.  
- [ ] User preference “Allow web tools” toggle (stored per user, enforced server-side).

---

## 9. Testing checklist

| Test | Type |
|------|------|
| MCP disabled | No tool registration; `mcpInvocations` empty; no badge in UI. |
| MCP enabled, model calls tool | Non-empty invocations; badge shows correct count. |
| Tool timeout / error | `success: false`; UI shows failure affordance; chat still returns answer or graceful error. |
| Cache hit | MCP not invoked; no MCP badge. |
| Security | Disallowed tool name never invoked; Fetch blocked for non-https if configured. |

---

## 10. Rollout

1. Ship **Phase 1–4** with `powerrag.mcp.enabled=false` by default.  
2. Enable on **staging** with Time + Fetch STDIO or sidecars.  
3. Enable on **production** with HTTP MCP servers only; monitor latency P95 on `POST /api/chat/query`.  
4. Iterate on prompt + allowlist based on abuse / cost.

---

## 11. References

- [Spring AI MCP overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)  
- [Spring AI — Getting started with MCP](https://docs.spring.io/spring-ai/reference/guides/getting-started-mcp.html)  
- [MCP client Boot starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)  
- [Official MCP servers (examples)](https://github.com/modelcontextprotocol/servers)

---

*Document version: 1.0 — aligned with Power RAG `RagService`, `RagResponse`, `Interaction`, and `ChatWindow` as of implementation planning.*
