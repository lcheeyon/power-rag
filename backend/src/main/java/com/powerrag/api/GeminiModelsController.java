package com.powerrag.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes Gemini models discoverable via the Google AI API for the chat model picker.
 */
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class GeminiModelsController {

    private final GeminiModelCatalogService geminiModelCatalogService;

    @GetMapping("/gemini")
    public ResponseEntity<List<GeminiModelInfo>> listGeminiModels() {
        return ResponseEntity.ok(geminiModelCatalogService.listChatModels());
    }
}
