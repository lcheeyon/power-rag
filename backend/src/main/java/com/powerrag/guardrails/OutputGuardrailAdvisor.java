package com.powerrag.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI {@link CallAdvisor} that checks the LLM response for PII and redacts it.
 */
@Slf4j
@Component
public class OutputGuardrailAdvisor implements CallAdvisor {

    private final GuardrailService guardrailService;

    public OutputGuardrailAdvisor(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);

        String content = extractContent(response);
        if (content == null) return response;

        GuardrailResult result = guardrailService.checkOutput(content);
        if (!result.passed()) {
            log.warn("Output PII detected: category={}", result.category());
            guardrailService.logFlag(null, "OUTPUT", result.category(), "WARN", content);
            String redacted = guardrailService.redactPii(content);
            ChatResponse redactedChatResponse = new ChatResponse(
                    List.of(new Generation(new AssistantMessage(redacted))));
            return new ChatClientResponse(redactedChatResponse, response.context());
        }
        return response;
    }

    @Override
    public String getName() { return "OutputGuardrailAdvisor"; }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    private String extractContent(ChatClientResponse response) {
        try {
            return response.chatResponse().getResult().getOutput().getText();
        } catch (Exception e) {
            return null;
        }
    }
}
