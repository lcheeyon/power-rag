package com.powerrag.rag.service;

import com.powerrag.cache.CacheHit;
import com.powerrag.cache.SemanticCache;
import com.powerrag.domain.Interaction;
import com.powerrag.domain.InteractionRepository;
import com.powerrag.domain.User;
import com.powerrag.guardrails.GuardrailResult;
import com.powerrag.guardrails.GuardrailService;
import com.powerrag.mcp.McpInvocationRecorder;
import com.powerrag.mcp.McpToolInvocationSummary;
import com.powerrag.mcp.ObservingToolCallback;
import com.powerrag.multilingual.MultilingualPromptBuilder;
import com.powerrag.rag.assembly.ContextAssembler;
import com.powerrag.rag.model.ImageGenerationResult;
import com.powerrag.rag.model.RagResponse;
import com.powerrag.rag.model.RetrievedChunk;
import com.powerrag.rag.model.SourceRef;
import com.powerrag.rag.intent.QueryIntent;
import com.powerrag.rag.intent.QueryIntentClassifier;
import com.powerrag.rag.retrieval.HybridRetriever;
import com.powerrag.rag.scoring.ConfidenceScorer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates the full RAG pipeline:
 * guardrail check → cache lookup → image generation (if requested) →
 * retrieve → score → assemble → LLM → output guardrail → cache store → audit.
 */
@Slf4j
@Service
public class RagService {

    static final String BLOCKED_MSG =
            "I'm sorry, but I cannot process this request as it violates content safety policies.";

    private static final String MCP_USER_HINT = """

[Tools: Use the provided tools for live data. For web pages use fetch_url — it returns JSON (read ok, status_code, text, error). Jira: JQL search or get-issue (e.g. KAN-5). For jira_search_issues JSON, follow presentation_hint: one issue per line with KEY, summary, and status — never concatenate keys (no KAN-5KAN-4). GitHub: github_search_code, github_get_repository_content. GCP logs: gcp_logging_query (log_filter). Time and weather: matching tools. Email (IMAP/SMTP): tools named email_* (e.g. email_list, email_search, email_get, email_read_body, email_reply, email_send, email_draft_create) — use email_accounts first if needed; summarize content carefully; for replies prefer drafting or quoting the user before sending. For get_current_time, if the user prompt includes a browser timezone line, use that IANA name for generic "what time is it" questions. When using the knowledge-base context above, cite [SOURCE n] as instructed.]""";

    private final HybridRetriever           retriever;
    private final ConfidenceScorer          scorer;
    private final ContextAssembler          assembler;
    private final InteractionRepository     interactionRepository;
    private final SemanticCache             semanticCache;
    private final MultilingualPromptBuilder promptBuilder;
    private final GuardrailService          guardrailService;
    private final ImageGenerationService    imageGenerationService;
    private final Map<String, ChatClient>   clientsByKey;
    private final ChatClient                ollamaBaseClient;
    private final ChatClient                geminiBaseClient;   // for dynamic Gemini model routing
    private final Optional<SyncMcpToolCallbackProvider> mcpToolCallbackProvider;
    private final McpInvocationRecorder               mcpInvocationRecorder;
    private final boolean                             mcpRagEnabled;
    private final QueryIntentClassifier               intentClassifier;
    private final boolean                             intentRoutingEnabled;

    public RagService(HybridRetriever retriever,
                      ConfidenceScorer scorer,
                      ContextAssembler assembler,
                      @Qualifier("claudeSonnet")   ChatClient claudeSonnet,
                      @Qualifier("claudeOpus")     ChatClient claudeOpus,
                      @Qualifier("claudeHaiku")    ChatClient claudeHaiku,
                      @Qualifier("geminiFlash")    ChatClient geminiFlash,
                      @Qualifier("geminiPro")      ChatClient geminiPro,
                      @Qualifier("ollamaQwen")     ChatClient ollamaQwen,
                      InteractionRepository interactionRepository,
                      SemanticCache semanticCache,
                      MultilingualPromptBuilder promptBuilder,
                      GuardrailService guardrailService,
                      ImageGenerationService imageGenerationService,
                      Optional<SyncMcpToolCallbackProvider> mcpToolCallbackProvider,
                      McpInvocationRecorder mcpInvocationRecorder,
                      QueryIntentClassifier intentClassifier,
                      @Value("${powerrag.mcp.rag-enabled:false}") boolean mcpRagEnabled,
                      @Value("${powerrag.rag.intent-routing-enabled:true}") boolean intentRoutingEnabled) {
        this.retriever              = retriever;
        this.scorer                 = scorer;
        this.assembler              = assembler;
        this.interactionRepository  = interactionRepository;
        this.semanticCache          = semanticCache;
        this.promptBuilder          = promptBuilder;
        this.guardrailService       = guardrailService;
        this.imageGenerationService = imageGenerationService;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
        this.mcpInvocationRecorder  = mcpInvocationRecorder;
        this.mcpRagEnabled          = mcpRagEnabled;
        this.intentClassifier       = intentClassifier;
        this.intentRoutingEnabled   = intentRoutingEnabled;
        this.ollamaBaseClient       = ollamaQwen;
        this.geminiBaseClient       = geminiFlash;  // base client — model overridden per request
        this.clientsByKey = Map.of(
            "ANTHROPIC:claude-sonnet-4-6",        claudeSonnet,
            "ANTHROPIC:claude-opus-4-6",          claudeOpus,
            "ANTHROPIC:claude-haiku-4-5-20251001",claudeHaiku,
            "GEMINI:gemini-2.5-flash",            geminiFlash,
            "GEMINI:gemini-2.5-pro",              geminiPro,
            "OLLAMA:qwen2.5-coder:7b",            ollamaQwen
        );
    }

    private ChatClient resolveClient(String provider, String modelId) {
        if (provider != null && modelId != null) {
            // Ollama and Gemini always route to their base client; model overridden at call time
            if ("OLLAMA".equalsIgnoreCase(provider)) return ollamaBaseClient;
            if ("GEMINI".equalsIgnoreCase(provider)) return geminiBaseClient;
            ChatClient c = clientsByKey.get(provider.toUpperCase() + ":" + modelId);
            if (c != null) return c;
        }
        return clientsByKey.get("ANTHROPIC:claude-sonnet-4-6");
    }

    /** Backward-compatible overload — no image. */
    @Transactional
    public RagResponse query(String question, UUID sessionId, User user, String language,
                             String modelProvider, String reqModelId) {
        return query(question, null, sessionId, user, language, modelProvider, reqModelId, null);
    }

    @Transactional
    public RagResponse query(String question, String imageBase64, UUID sessionId, User user,
                             String language, String modelProvider, String reqModelId) {
        return query(question, imageBase64, sessionId, user, language, modelProvider, reqModelId, null);
    }

    @Transactional
    public RagResponse query(String question, String imageBase64, UUID sessionId, User user,
                             String language, String modelProvider, String reqModelId,
                             String clientTimezone) {
        long       start      = System.currentTimeMillis();
        String     lang       = language != null ? language : "en";
        UUID       session    = sessionId != null ? sessionId : UUID.randomUUID();
        ChatClient client     = resolveClient(modelProvider, reqModelId);
        String     resolvedId = (reqModelId != null) ? reqModelId : "claude-sonnet-4-6";
        String     resolvedProvider = (modelProvider != null) ? modelProvider.toUpperCase() : "ANTHROPIC";

        // ── 0. Input guardrail ─────────────────────────────────────────────
        GuardrailResult inputCheck = guardrailService.checkInput(question);
        if (!inputCheck.passed()) {
            log.warn("Input blocked by guardrail: category={}", inputCheck.category());
            long durationMs = System.currentTimeMillis() - start;
            guardrailService.logFlag(null, "INPUT", inputCheck.category(), "BLOCK", question);
            Interaction saved = interactionRepository.save(Interaction.builder()
                    .user(user).sessionId(session)
                    .queryText(question).queryLanguage(lang)
                    .responseText(BLOCKED_MSG).responseLanguage(lang)
                    .modelProvider("GUARDRAIL").modelId(guardrailService.inputModelId())
                    .confidence(0.0).sources(List.of()).cacheHit(false)
                    .guardrailFlag(true).flagReason("INPUT_BLOCKED: " + inputCheck.category())
                    .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                    .build());
            return new RagResponse(BLOCKED_MSG, 0.0, List.of(), "BLOCKED", durationMs, saved.getId(), false, null, null, List.of());
        }

        // ── 1. Semantic cache lookup ───────────────────────────────────────
        Optional<CacheHit> cached = semanticCache.lookup(question, lang);
        if (cached.isPresent()) {
            CacheHit hit        = cached.get();
            long     durationMs = System.currentTimeMillis() - start;

            Interaction saved = interactionRepository.save(Interaction.builder()
                    .user(user)
                    .sessionId(session)
                    .queryText(question)
                    .queryLanguage(lang)
                    .responseText(hit.answer())
                    .responseLanguage(lang)
                    .modelProvider("ANTHROPIC")
                    .modelId(hit.modelId())
                    .confidence(hit.confidence())
                    .sources(sourcesToMaps(hit.sources()))
                    .cacheHit(true)
                    .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                    .build());

            log.info("RAG cache HIT in {}ms — lang={}", durationMs, lang);
            return new RagResponse(hit.answer(), hit.confidence(), hit.sources(),
                    hit.modelId(), durationMs, saved.getId(), true, null, null, List.of());
        }

        // ── 1.5. Image generation intent (Nano Banana / Imagen — not the selected text chat model) ──
        if (imageGenerationService.isImageGenerationRequest(question)) {
            String preferredImageModel = "GEMINI".equalsIgnoreCase(resolvedProvider)
                    && resolvedId != null && resolvedId.toLowerCase().contains("image")
                    ? resolvedId
                    : null;
            ImageGenerationResult generated = imageGenerationService.generateImage(question, preferredImageModel);
            if (generated != null && generated.ok()) {
                long durationMs = System.currentTimeMillis() - start;
                String genAnswer = "Here is the generated image based on your request.";
                Interaction saved = interactionRepository.save(Interaction.builder()
                        .user(user).sessionId(session)
                        .queryText(question).queryLanguage(lang)
                        .responseText(genAnswer).responseLanguage(lang)
                        .modelProvider("GOOGLE").modelId(generated.modelId())
                        .confidence(1.0).sources(List.of()).cacheHit(false)
                        .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                        .build());
                log.info("Image generated with {} in {}ms", generated.modelId(), durationMs);
                return new RagResponse(genAnswer, 1.0, List.of(), generated.modelId(),
                        durationMs, saved.getId(), false, null, generated.dataUrl(), List.of());
            }
            if (!imageGenerationService.fallbackToLlmOnFailure()) {
                long durationMs = System.currentTimeMillis() - start;
                String msg = "Image generation did not return a picture. Check that GOOGLE_API_KEY is set and "
                        + "your project can use Gemini image models (e.g. gemini-3-pro-image-preview, Nano Banana Pro). "
                        + "You can pick a Gemini image model in the model list to prefer that endpoint.";
                Interaction saved = interactionRepository.save(Interaction.builder()
                        .user(user).sessionId(session)
                        .queryText(question).queryLanguage(lang)
                        .responseText(msg).responseLanguage(lang)
                        .modelProvider("GOOGLE").modelId("image-generation")
                        .confidence(0.0).sources(List.of()).cacheHit(false)
                        .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                        .build());
                return new RagResponse(msg, 0.0, List.of(), "image-generation",
                        durationMs, saved.getId(), false, null, null, List.of());
            }
            // Else fall through to the main LLM
        }

        // ── 2. Intent routing (before retrieval & main LLM) ─────────────────
        boolean imagePresent = imageBase64 != null && !imageBase64.isBlank();
        QueryIntent intent;
        if (!intentRoutingEnabled || imagePresent) {
            intent = QueryIntent.legacy(mcpRagEnabled);
        } else {
            intent = intentClassifier.classify(question, lang, client, modelProvider, reqModelId);
        }
        boolean attachMcpTools = mcpRagEnabled && intent.allowMcpTools();
        log.info("Query intent: retrieveDocuments={} attachMcpTools={} (routingActive={})",
                intent.retrieveDocuments(), attachMcpTools, intentRoutingEnabled && !imagePresent);

        // ── 3. Retrieval (optional) ─────────────────────────────────────────
        List<RetrievedChunk> chunks;
        if (!intent.retrieveDocuments()) {
            chunks = List.of();
            log.info("Skipping knowledge-base retrieval (intent.retrieveDocuments=false)");
        } else {
            chunks = retriever.retrieve(question);
        }
        double retrievalConfidence = scorer.score(chunks);
        boolean hasRelevantDocs    = retrievalConfidence >= 0.1 && !chunks.isEmpty();
        String  context            = hasRelevantDocs ? assembler.assemble(chunks) : "";
        List<SourceRef> sources    = assembler.extractSources(chunks);

        if (intent.retrieveDocuments() && !hasRelevantDocs) {
            log.info("No relevant docs found (retrievalConfidence={}), falling back to LLM general knowledge",
                    retrievalConfidence);
        }

        String answer;
        String llmError = null;
        List<McpToolInvocationSummary> mcpInvocations = List.of();
        try {
            LlmCallOutcome llmOutcome = callLlmWithMcp(question, context, lang, client, hasRelevantDocs,
                    modelProvider, reqModelId, imageBase64, attachMcpTools, clientTimezone);
            answer = llmOutcome.answer();
            mcpInvocations = llmOutcome.mcpInvocations();
        } catch (Exception e) {
            llmError = extractErrorMessage(e.getMessage());
            log.error("LLM call failed: {}", e.getMessage());
            answer = "";
        }
        long durationMs = System.currentTimeMillis() - start;

        if (llmError != null) {
            return new RagResponse("", retrievalConfidence, sources, resolvedId, durationMs, null, false, llmError, null, List.of());
        }

        if (answer == null) {
            answer = "";
        }

        double confidence = scorer.responseConfidence(retrievalConfidence, hasRelevantDocs, mcpInvocations);

        // ── 4. Store in semantic cache ───────────────────────────────────────
        if (!answer.isBlank()) {
            semanticCache.store(question, lang, answer, confidence, sources, resolvedId);
        }

        // ── 5. Audit log ───────────────────────────────────────────────────
        List<String> topChunkIds = chunks.stream()
                .map(RetrievedChunk::qdrantId)
                .toList();

        Interaction saved = interactionRepository.save(Interaction.builder()
                .user(user)
                .sessionId(session)
                .queryText(question)
                .queryLanguage(lang)
                .responseText(answer)
                .responseLanguage(lang)
                .modelProvider(resolvedProvider)
                .modelId(resolvedId)
                .confidence(confidence)
                .sources(sourcesToMaps(sources))
                .topChunkIds(topChunkIds)
                .mcpInvocations(mcpSummariesToMaps(mcpInvocations))
                .cacheHit(false)
                .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                .build());

        log.info("RAG query completed in {}ms — provider={} model={} confidence={} (retrieval={}) sources={} lang={} mcpTools={}",
                durationMs, resolvedProvider, resolvedId, confidence, retrievalConfidence, sources.size(), lang, mcpInvocations.size());

        return new RagResponse(answer, confidence, sources, resolvedId, durationMs, saved.getId(), false, null, null, mcpInvocations);
    }

    // ── package-visible for testing ───────────────────────────────────────

    String callLlm(String question, String context, String language, ChatClient client,
                   boolean hasRelevantDocs) {
        return callLlmWithMcp(question, context, language, client, hasRelevantDocs, null, null, null, mcpRagEnabled, null).answer();
    }

    String callLlm(String question, String context, String language, ChatClient client,
                   boolean hasRelevantDocs, String provider, String modelId) {
        return callLlmWithMcp(question, context, language, client, hasRelevantDocs, provider, modelId, null, mcpRagEnabled, null).answer();
    }

    String callLlm(String question, String context, String language, ChatClient client,
                   boolean hasRelevantDocs, String provider, String modelId, String imageBase64) {
        return callLlmWithMcp(question, context, language, client, hasRelevantDocs, provider, modelId, imageBase64, mcpRagEnabled, null).answer();
    }

    private record LlmCallOutcome(String answer, List<McpToolInvocationSummary> mcpInvocations) {}

    private LlmCallOutcome callLlmWithMcp(String question, String context, String language, ChatClient client,
                                          boolean hasRelevantDocs, String provider, String modelId, String imageBase64,
                                          boolean attachMcpTools, String clientTimezone) {
        boolean imagePresent = imageBase64 != null && !imageBase64.isBlank();
        String userMessage = promptBuilder.buildUserMessage(question, context, language, hasRelevantDocs, imagePresent,
                clientTimezone);

        ChatClient.ChatClientRequestSpec baseSpec = client.prompt();

        if ("OLLAMA".equalsIgnoreCase(provider) && modelId != null) {
            baseSpec = baseSpec.options(OllamaChatOptions.builder().model(modelId).build());
        } else if ("GEMINI".equalsIgnoreCase(provider) && modelId != null) {
            baseSpec = baseSpec.options(GoogleGenAiChatOptions.builder().model(modelId).build());
        }

        ToolCallback[] mcpTools = attachMcpTools && !imagePresent ? resolveMcpToolCallbacks() : null;
        if (mcpTools != null && mcpTools.length > 0) {
            userMessage = userMessage + MCP_USER_HINT;
        }

        ChatClient.ChatClientRequestSpec promptSpec;
        if (imagePresent) {
            promptSpec = baseSpec.messages(buildUserMessageWithImage(userMessage, imageBase64));
        } else {
            promptSpec = baseSpec.user(userMessage);
        }

        if (mcpTools != null && mcpTools.length > 0) {
            promptSpec = promptSpec.toolCallbacks(mcpTools);
        }

        mcpInvocationRecorder.clear();
        String rawAnswer;
        try {
            rawAnswer = promptSpec.call().content();
        } catch (RuntimeException e) {
            mcpInvocationRecorder.snapshotAndClear();
            throw e;
        }
        if (rawAnswer == null) {
            rawAnswer = "";
        }
        List<McpToolInvocationSummary> invocations = mcpInvocationRecorder.snapshotAndClear();

        GuardrailResult outputCheck = guardrailService.checkOutput(rawAnswer);
        if (!outputCheck.passed()) {
            log.warn("Output PII detected: category={}", outputCheck.category());
            guardrailService.logFlag(null, "OUTPUT", outputCheck.category(), "WARN", rawAnswer);
            return new LlmCallOutcome(guardrailService.redactPii(rawAnswer), invocations);
        }
        return new LlmCallOutcome(rawAnswer, invocations);
    }

    /** Returns wrapped MCP tool callbacks, or {@code null} when MCP is off or unavailable. */
    private ToolCallback[] resolveMcpToolCallbacks() {
        if (!mcpRagEnabled || mcpToolCallbackProvider.isEmpty()) {
            return null;
        }
        ToolCallback[] raw = mcpToolCallbackProvider.get().getToolCallbacks();
        if (raw == null || raw.length == 0) {
            return null;
        }
        return ObservingToolCallback.wrapAll(raw, mcpInvocationRecorder);
    }

    private static UserMessage buildUserMessageWithImage(String text, String imageBase64) {
        String mimeTypeStr = "image/jpeg";
        if (imageBase64.startsWith("data:")) {
            int semi = imageBase64.indexOf(';');
            if (semi > 5) mimeTypeStr = imageBase64.substring(5, semi);
        }
        String encoded = imageBase64.contains(",") ? imageBase64.split(",", 2)[1] : imageBase64;
        byte[] imageBytes = Base64.getDecoder().decode(encoded);
        Media media = new Media(MimeType.valueOf(mimeTypeStr), new ByteArrayResource(imageBytes));
        return UserMessage.builder().text(text).media(media).build();
    }

    private static final Pattern JSON_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");

    private static String extractErrorMessage(String raw) {
        if (raw == null) return "Unknown error";
        Matcher m = JSON_MESSAGE.matcher(raw);
        String last = null;
        while (m.find()) last = m.group(1);
        return last != null ? last : raw;
    }

    private List<Map<String, Object>> sourcesToMaps(List<SourceRef> sources) {
        return sources.stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("fileName", s.fileName());
                    m.put("section",  s.section());
                    m.put("snippet",  s.snippet());
                    return m;
                })
                .toList();
    }

    private List<Map<String, Object>> mcpSummariesToMaps(List<McpToolInvocationSummary> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("serverId", s.serverId());
            m.put("toolName", s.toolName());
            m.put("success", s.success());
            m.put("durationMs", s.durationMs());
            if (s.errorMessage() != null) m.put("errorMessage", s.errorMessage());
            if (s.argsSummary() != null) m.put("argsSummary", s.argsSummary());
            return m;
        }).toList();
    }
}
