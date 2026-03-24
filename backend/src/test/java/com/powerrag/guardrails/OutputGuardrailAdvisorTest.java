package com.powerrag.guardrails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutputGuardrailAdvisor Unit Tests")
class OutputGuardrailAdvisorTest {

    @Mock GuardrailService  guardrailService;
    @Mock CallAdvisorChain  chain;

    OutputGuardrailAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new OutputGuardrailAdvisor(guardrailService);
    }

    private ChatClientRequest buildRequest() {
        return new ChatClientRequest(new Prompt(new UserMessage("question")), Map.of());
    }

    private ChatClientResponse buildChainResponse(String content) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))));
        return new ChatClientResponse(chatResponse, Map.of());
    }

    @Test
    @DisplayName("Safe output: response returned unchanged")
    void safeOutput_returnedUnchanged() {
        ChatClientResponse chainResp = buildChainResponse("Safe RAG answer.");
        when(chain.nextCall(any())).thenReturn(chainResp);
        when(guardrailService.checkOutput("Safe RAG answer.")).thenReturn(GuardrailResult.safe());

        ChatClientResponse result = advisor.adviseCall(buildRequest(), chain);

        assertThat(result).isSameAs(chainResp);
        verify(guardrailService, never()).logFlag(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PII in output: content is redacted")
    void piiOutput_contentRedacted() {
        ChatClientResponse chainResp = buildChainResponse("Contact admin@example.com for support.");
        when(chain.nextCall(any())).thenReturn(chainResp);
        when(guardrailService.checkOutput(anyString()))
                .thenReturn(GuardrailResult.unsafe("PII_EMAIL"));
        when(guardrailService.redactPii("Contact admin@example.com for support."))
                .thenReturn("Contact [EMAIL REDACTED] for support.");

        ChatClientResponse result = advisor.adviseCall(buildRequest(), chain);

        String content = result.chatResponse().getResult().getOutput().getText();
        assertThat(content).isEqualTo("Contact [EMAIL REDACTED] for support.");
    }

    @Test
    @DisplayName("PII in output: logFlag called with OUTPUT/WARN")
    void piiOutput_logFlagCalledWithWarn() {
        ChatClientResponse chainResp = buildChainResponse("SSN: 123-45-6789");
        when(chain.nextCall(any())).thenReturn(chainResp);
        when(guardrailService.checkOutput(anyString()))
                .thenReturn(GuardrailResult.unsafe("PII_SSN"));
        when(guardrailService.redactPii(anyString())).thenReturn("[REDACTED]");

        advisor.adviseCall(buildRequest(), chain);

        verify(guardrailService).logFlag(null, "OUTPUT", "PII_SSN", "WARN", "SSN: 123-45-6789");
    }

    @Test
    @DisplayName("getName returns OutputGuardrailAdvisor")
    void getName_returnsCorrectName() {
        assertThat(advisor.getName()).isEqualTo("OutputGuardrailAdvisor");
    }

    @Test
    @DisplayName("getOrder returns LOWEST_PRECEDENCE")
    void getOrder_lowestPrecedence() {
        assertThat(advisor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}
