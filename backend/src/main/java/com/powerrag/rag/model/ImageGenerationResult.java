package com.powerrag.rag.model;

/**
 * Outcome of {@link com.powerrag.rag.service.ImageGenerationService#generateImage}.
 */
public record ImageGenerationResult(String dataUrl, String modelId) {

    public boolean ok() {
        return dataUrl != null && !dataUrl.isBlank() && modelId != null && !modelId.isBlank();
    }
}
