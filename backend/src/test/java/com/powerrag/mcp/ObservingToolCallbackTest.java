package com.powerrag.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservingToolCallbackTest {

    @Test
    void normalizeMcpToolOutput_unwrapsTextContentToString() {
        String wrapped = "TextContent[annotations=null, text={\"ok\":false,\"error\":\"HTTP 404\"}, meta=null]";
        assertThat(ObservingToolCallback.normalizeMcpToolOutput(wrapped))
                .isEqualTo("{\"ok\":false,\"error\":\"HTTP 404\"}");
    }

    @Test
    void normalizeMcpToolOutput_passesThroughPlainJson() {
        String json = "{\"ok\":true,\"text\":\"hello\"}";
        assertThat(ObservingToolCallback.normalizeMcpToolOutput(json)).isEqualTo(json);
    }

    @Test
    void normalizeMcpToolOutput_handlesFetchErrorStyleMessage() {
        String wrapped =
                "TextContent[annotations=null, text=Failed to fetch https://example.com - status code 404, meta=null]";
        assertThat(ObservingToolCallback.normalizeMcpToolOutput(wrapped))
                .isEqualTo("Failed to fetch https://example.com - status code 404");
    }

    @Test
    void normalizeMcpToolOutput_nullSafe() {
        assertThat(ObservingToolCallback.normalizeMcpToolOutput(null)).isNull();
    }
}
