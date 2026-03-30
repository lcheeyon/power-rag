package com.powerrag.api;

import com.powerrag.domain.User;
import com.powerrag.domain.UserRepository;
import com.powerrag.rag.model.RagResponse;
import com.powerrag.rag.service.RagService;
import jakarta.validation.Valid;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chat / RAG query endpoint.
 * GET  /api/chat/query — lightweight probe kept for the health-check BDD scenario.
 * POST /api/chat/query — full RAG pipeline via RagService.
 *
 * <p>Language resolution order:
 * <ol>
 *   <li>Explicit {@code language} field in the request body</li>
 *   <li>Authenticated user's {@code preferredLanguage} setting</li>
 *   <li>Default: {@code "en"}</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagService                            ragService;
    private final UserRepository                        userRepository;
    private final Optional<SyncMcpToolCallbackProvider> mcpToolCallbackProvider;

    @Value("${powerrag.mcp.rag-enabled:false}")
    private boolean mcpRagEnabled;

    public ChatController(RagService ragService,
                          UserRepository userRepository,
                          Optional<SyncMcpToolCallbackProvider> mcpToolCallbackProvider) {
        this.ragService                 = ragService;
        this.userRepository             = userRepository;
        this.mcpToolCallbackProvider    = mcpToolCallbackProvider;
    }

    /** Retained for the existing auth-check BDD scenario (GET with JWT → not 401). */
    @GetMapping("/query")
    public ResponseEntity<Map<String, String>> queryProbe() {
        return ResponseEntity.ok(Map.of("status", "OK", "message", "Use POST for RAG queries"));
    }

    /**
     * Lists MCP tools currently registered with the Spring AI client (e.g. fetch from {@code application-dev.yml}).
     */
    @GetMapping("/mcp-tools")
    public ResponseEntity<McpToolsResponse> listMcpTools() {
        boolean clientOk = mcpToolCallbackProvider.isPresent();
        List<McpToolsResponse.McpToolEntry> tools = new ArrayList<>();
        if (clientOk) {
            ToolCallback[] callbacks = mcpToolCallbackProvider.get().getToolCallbacks();
            if (callbacks != null) {
                for (ToolCallback cb : callbacks) {
                    var def = cb.getToolDefinition();
                    String desc = def.description();
                    tools.add(new McpToolsResponse.McpToolEntry(def.name(), desc != null ? desc : ""));
                }
            }
        }
        return ResponseEntity.ok(new McpToolsResponse(mcpRagEnabled, clientOk, List.copyOf(tools)));
    }

    @PostMapping("/query")
    public ResponseEntity<RagResponse> query(
            @RequestBody @Valid ChatQueryRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User   user      = userRepository.findByUsername(principal.getUsername()).orElse(null);
        UUID   sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID();
        String language  = resolveLanguage(request.language(), user);

        RagResponse response = ragService.query(request.question(), request.imageBase64(),
                sessionId, user, language, request.modelProvider(), request.modelId(), request.clientTimezone());
        return ResponseEntity.ok(response);
    }

    /** Returns the explicit request language, then the user's preference, then "en". */
    private String resolveLanguage(String requestLanguage, User user) {
        if (requestLanguage != null && !requestLanguage.isBlank()) return requestLanguage;
        if (user != null && user.getPreferredLanguage() != null
                && !user.getPreferredLanguage().isBlank()) {
            return user.getPreferredLanguage();
        }
        return "en";
    }
}
