# Development Guide

## Embeddings, semantic cache, and input guardrails

The **local stack standardizes on 768-dimensional** vectors for `gemini-embedding-001`.

- **Canonical setting** — `powerrag.embedding.dimensions` (default **768**, override with env **`POWERRAG_EMBEDDING_DIMENSIONS`**). This value is wired into both `spring.ai.google.genai.embedding.text.options.dimensions` and `spring.ai.vectorstore.qdrant.dimensions`, so Qdrant, ingestion, and query embeddings stay aligned.
- **Semantic cache (Redis Stack)** — uses the same `EmbeddingModel` bean, so its index width matches **768** automatically.
- **Input guardrails** — **Gemini 2.5 Flash** (`gemini-2.5-flash`) via `powerrag.guardrails.input-model-id` (env: `POWERRAG_GUARDRAIL_MODEL`).

`OllamaEmbeddingAutoConfiguration` is **excluded** in `application.yml` so the knowledge base and Redis cache always share the same cloud `EmbeddingModel`. Ollama remains available for **local chat** only.

If Qdrant was ever created with another size (e.g. Gemini’s API default **3072**), dense search will fail until you **delete** the collection and let the app recreate it at **768**, then **re-ingest** documents. Docker Compose sets `POWERRAG_EMBEDDING_DIMENSIONS=768` on the backend for clarity.

## Adding a New LLM Provider

1. **Add Spring AI starter** in `backend/pom.xml`:
   ```xml
   <dependency>
     <groupId>org.springframework.ai</groupId>
     <artifactId>spring-ai-starter-model-<provider></artifactId>
   </dependency>
   ```

2. **Register a `ChatClient` bean** in `SpringAiConfig.java`:
   ```java
   @Bean
   @Qualifier("myProvider")
   public ChatClient myProviderClient(MyProviderChatModel model) {
       return ChatClient.builder(model)
               .defaultSystem(SYSTEM_PREAMBLE)
               .build();
   }
   ```

3. **Add to `RagService` constructor** and `clientsByKey` map:
   ```java
   "MYPROVIDER:my-model-id", myProviderClient
   ```

4. **Add to `ModelSelector.tsx`** in the frontend:
   ```typescript
   { provider: 'MYPROVIDER', modelId: 'my-model-id', label: 'My Model', emoji: '🤖' }
   ```

5. **Add API key config** to `application.yml` and `.env.example`.

---

## Adding a New Document Parser

1. Implement `DocumentParser` interface:
   ```java
   @Component
   public class MyFormatParser implements DocumentParser {
       @Override
       public String supportedExtension() { return "myext"; }

       @Override
       public List<ParsedSection> parse(InputStream input, String fileName) {
           // Return list of ParsedSection with text + metadata map
       }
   }
   ```
   Spring's `List<DocumentParser>` auto-injection will pick it up.

2. Add the file type to `DocumentIngestionService.resolveFileType()`.

3. Write a unit test in `src/test/java/.../ingestion/parser/MyFormatParserTest.java`.

---

## Adding a New API Endpoint

1. Create (or add to) a `@RestController` in `src/main/java/com/powerrag/api/`.

2. Secure it in `SecurityConfig.java`:
   ```java
   .requestMatchers("/api/my-endpoint/**").hasRole("USER")
   ```

3. Add an Axios client function in `frontend/src/api/`.

4. Write a controller unit test using `@WebMvcTest` + MockMvc.

---

## Database Migrations

Never modify an existing migration file — Flyway validates checksums.

To change the schema:
```bash
# Create the next migration
touch backend/src/main/resources/db/migration/V8__my_change.sql
```

Write idempotent SQL (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`).

---

## Environment Setup for Tests

Backend unit tests use Mockito and need no external services.

Integration tests use **Testcontainers** — Docker must be running:
```bash
# Run only unit tests (fast)
cd backend && ./mvnw test -Dtest="*Test" -DfailIfNoTests=false

# Run integration tests (requires Docker)
cd backend && ./mvnw verify -Dtest="*IntegrationTest"

# Run all including BDD
cd backend && ./mvnw verify
```

Frontend tests use **Vitest** + **MSW** (Mock Service Worker) for API mocking:
```bash
cd frontend && npm test
```

---

## MCP on the laptop (dev profile only)

MCP is **off** in the default `application.yml` so CI and default JAR runs do not need extra stdio binaries. For local chat with tools, the **powerrag-tools** Python server provides **`fetch_url`** (always JSON, avoids Gemini tool JSON parse issues on HTTP errors), time, weather, Jira, GitHub, and GCP Logging.

- **Docker Compose**: backend image runs `pip3 install -r mcp/requirements.txt`; stack uses `SPRING_PROFILES_ACTIVE=dev` with `application-dev.yml` (MCP `request-timeout` 120s).
- **Host**: run with the **dev** profile after `pip3 install -r backend/mcp/requirements.txt`:

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

`application-dev.yml` sets `powerrag.mcp.rag-enabled: true` and registers the **powerrag-tools** Python MCP server via stdio. Successful tool calls appear in the chat UI under an **MCP** badge (`data-testid="mcp-tools-badge"`). Production / GCP configs are unchanged and keep MCP disabled.

---

## Code Coverage

JaCoCo enforces thresholds on `mvn verify`:
- **Line coverage ≥ 80%**
- **Branch coverage ≥ 75%**

Excluded from coverage: `PowerRagApplication.class`, `domain/**` (entities/repositories).

View the HTML report after `./mvnw verify`:
```
open backend/target/site/jacoco/index.html
```

---

## Logging

The backend uses SLF4J + Logback (Spring Boot default). Key logger levels in `application.yml`:

```yaml
logging:
  level:
    com.powerrag: INFO       # Application code
    org.springframework.ai: INFO
    org.springframework.security: WARN
    org.flywaydb: INFO
```

For debugging LLM calls, set `org.springframework.ai: DEBUG`.
For debugging security, set `org.springframework.security: DEBUG`.

---

## Useful Scripts

```bash
# Restart backend (kills port 8080 and relaunches)
lsof -ti:8080 | xargs kill -9 2>/dev/null; cd backend && export $(grep -v "^#" ../.env | xargs) && ./mvnw spring-boot:run

# Wipe Qdrant KB collection (wrong dimension, corrupt index, or after changing POWERRAG_EMBEDDING_DIMENSIONS)
chmod +x scripts/reset-qdrant-collection.sh && ./scripts/reset-qdrant-collection.sh
# Restart backend — it recreates power_rag_docs at 768; re-upload documents

# Manually register a Flyway migration checksum (if you applied SQL directly)
python3 -c "
import zlib, struct
data = open('backend/src/main/resources/db/migration/VX__file.sql','rb').read()
crc = 0
for line in data.splitlines():
    crc = zlib.crc32(line, crc)
print(struct.unpack('i', struct.pack('I', crc & 0xFFFFFFFF))[0])
"
```
