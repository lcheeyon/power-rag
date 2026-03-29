package com.powerrag.rag.service;

import com.powerrag.cache.CacheHit;
import com.powerrag.cache.SemanticCache;
import com.powerrag.domain.Interaction;
import com.powerrag.domain.InteractionRepository;
import com.powerrag.domain.User;
import com.powerrag.guardrails.GuardrailResult;
import com.powerrag.guardrails.GuardrailService;
import com.powerrag.multilingual.MultilingualPromptBuilder;
import com.powerrag.rag.assembly.ContextAssembler;
import com.powerrag.rag.model.RagResponse;
import com.powerrag.rag.model.RetrievedChunk;
import com.powerrag.rag.model.SourceRef;
import com.powerrag.rag.retrieval.HybridRetriever;
import com.powerrag.rag.scoring.ConfidenceScorer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;

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
                      ImageGenerationService imageGenerationService) {
        this.retriever              = retriever;
        this.scorer                 = scorer;
        this.assembler              = assembler;
        this.interactionRepository  = interactionRepository;
        this.semanticCache          = semanticCache;
        this.promptBuilder          = promptBuilder;
        this.guardrailService       = guardrailService;
        this.imageGenerationService = imageGenerationService;
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
        return query(question, null, sessionId, user, language, modelProvider, reqModelId);
    }

    @Transactional
    public RagResponse query(String question, String imageBase64, UUID sessionId, User user,
                             String language, String modelProvider, String reqModelId) {
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
            return new RagResponse(BLOCKED_MSG, 0.0, List.of(), "BLOCKED", durationMs, saved.getId(), false, null, null);
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
                    hit.modelId(), durationMs, saved.getId(), true, null, null);
        }

        // ── 1.5. Image generation intent ──────────────────────────────────
        if (imageGenerationService.isImageGenerationRequest(question)) {
            String generatedImage = imageGenerationService.generateImage(question);
            if (generatedImage != null) {
                long durationMs = System.currentTimeMillis() - start;
                String genAnswer = "Here is the generated image based on your request.";
                Interaction saved = interactionRepository.save(Interaction.builder()
                        .user(user).sessionId(session)
                        .queryText(question).queryLanguage(lang)
                        .responseText(genAnswer).responseLanguage(lang)
                        .modelProvider("GOOGLE").modelId("imagen-3.0-generate-002")
                        .confidence(1.0).sources(List.of()).cacheHit(false)
                        .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                        .build());
                log.info("Image generated in {}ms", durationMs);
                return new RagResponse(genAnswer, 1.0, List.of(), "imagen-3.0-generate-002",
                        durationMs, saved.getId(), false, null, generatedImage);
            }
            // If generation failed, fall through to LLM to handle gracefully
        }

        // ── 2. Full RAG pipeline ────────────────────────────────────────────
        List<RetrievedChunk> chunks          = retriever.retrieve(question);
        double               confidence      = scorer.score(chunks);
        boolean              hasRelevantDocs = confidence >= 0.1 && !chunks.isEmpty();
        String               context         = hasRelevantDocs ? assembler.assemble(chunks) : "";
        List<SourceRef>      sources         = assembler.extractSources(chunks);

        if (!hasRelevantDocs) {
            log.info("No relevant docs found (confidence={}), falling back to LLM general knowledge", confidence);
        }

        String answer;
        String llmError = null;
        try {
            answer = callLlm(question, context, lang, client, hasRelevantDocs, modelProvider, reqModelId, imageBase64);
        } catch (Exception e) {
            llmError = extractErrorMessage(e.getMessage());
            log.error("LLM call failed: {}", e.getMessage());
            answer = "";
        }
        long durationMs = System.currentTimeMillis() - start;

        if (llmError != null) {
            return new RagResponse("", confidence, sources, resolvedId, durationMs, null, false, llmError, null);
        }

        // ── 3. Store in semantic cache ─────────────────────────────────────
        semanticCache.store(question, lang, answer, confidence, sources, resolvedId);

        // ── 4. Audit log ───────────────────────────────────────────────────
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
                .cacheHit(false)
                .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                .build());

        log.info("RAG query completed in {}ms — provider={} model={} confidence={} sources={} lang={}",
                durationMs, resolvedProvider, resolvedId, confidence, sources.size(), lang);

        return new RagResponse(answer, confidence, sources, resolvedId, durationMs, saved.getId(), false, null, null);
    }

    // ── package-visible for testing ───────────────────────────────────────

    String callLlm(String question, String context, String language, ChatClient client,
                   boolean hasRelevantDocs) {
        return callLlm(question, context, language, client, hasRelevantDocs, null, null, null);
    }

    String callLlm(String question, String context, String language, ChatClient client,
                   boolean hasRelevantDocs, String provider, String modelId) {
        return callLlm(question, context, language, client, hasRelevantDocs, provider, modelId, null);
    }

    String callLlm(String question, String context, String language, ChatClient client,
                   boolean hasRelevantDocs, String provider, String modelId, String imageBase64) {
        boolean imagePresent = imageBase64 != null && !imageBase64.isBlank();
        String userMessage = promptBuilder.buildUserMessage(question, context, language, hasRelevantDocs, imagePresent);
        String rawAnswer;

        ChatClient.ChatClientRequestSpec baseSpec = client.prompt();

        // Override model at request time for dynamic-routing providers
        if ("OLLAMA".equalsIgnoreCase(provider) && modelId != null) {
            baseSpec = baseSpec.options(OllamaChatOptions.builder().model(modelId).build());
        } else if ("GEMINI".equalsIgnoreCase(provider) && modelId != null) {
            baseSpec = baseSpec.options(GoogleGenAiChatOptions.builder().model(modelId).build());
        }

        ChatClient.ChatClientRequestSpec promptSpec;
        if (imagePresent) {
            promptSpec = baseSpec.messages(buildUserMessageWithImage(userMessage, imageBase64));
        } else {
            promptSpec = baseSpec.user(userMessage);
        }

        rawAnswer = promptSpec.call().content();

        // ── Output guardrail: redact PII ──────────────────────────────────
        GuardrailResult outputCheck = guardrailService.checkOutput(rawAnswer);
        if (!outputCheck.passed()) {
            log.warn("Output PII detected: category={}", outputCheck.category());
            guardrailService.logFlag(null, "OUTPUT", outputCheck.category(), "WARN", rawAnswer);
            return guardrailService.redactPii(rawAnswer);
        }
        return rawAnswer;
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
}
