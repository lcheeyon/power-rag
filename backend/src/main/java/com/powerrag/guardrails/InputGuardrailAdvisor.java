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
 * Spring AI {@link CallAdvisor} that checks user input before it reaches the LLM.
 * Unsafe inputs are blocked and a flag is logged; the LLM chain is never called.
 */
@Slf4j
@Component
public class InputGuardrailAdvisor implements CallAdvisor {

    static final String BLOCKED_MSG =
            "I'm sorry, but I cannot process this request as it violates content safety policies.";

    private final GuardrailService guardrailService;

    public InputGuardrailAdvisor(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userText = extractUserText(request);
        GuardrailResult result = guardrailService.checkInput(userText);

        if (!result.passed()) {
            log.warn("Input blocked by guardrail: category={}", result.category());
            guardrailService.logFlag(null, "INPUT", result.category(), "BLOCK", userText);
            return blockedResponse(request);
        }
        return chain.nextCall(request);
    }

    @Override
    public String getName() { return "InputGuardrailAdvisor"; }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    private String extractUserText(ChatClientRequest request) {
        try {
            var userMsg = request.prompt().getUserMessage();
            return userMsg != null ? userMsg.getText() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private ChatClientResponse blockedResponse(ChatClientRequest request) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(BLOCKED_MSG))));
        return new ChatClientResponse(chatResponse, request.context());
    }
}
