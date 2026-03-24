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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InputGuardrailAdvisor Unit Tests")
class InputGuardrailAdvisorTest {

    @Mock GuardrailService    guardrailService;
    @Mock CallAdvisorChain    chain;
    @Mock ChatClientResponse  chainResponse;

    InputGuardrailAdvisor advisor;

    private ChatClientRequest buildRequest(String userText) {
        Prompt prompt = new Prompt(new UserMessage(userText));
        return new ChatClientRequest(prompt, Map.of());
    }

    @BeforeEach
    void setUp() {
        advisor = new InputGuardrailAdvisor(guardrailService);
        lenient().when(chain.nextCall(any())).thenReturn(chainResponse);
    }

    @Test
    @DisplayName("Safe input: chain is called and response returned")
    void safeInput_chainCalled() {
        when(guardrailService.checkInput(anyString())).thenReturn(GuardrailResult.safe());

        ChatClientResponse response = advisor.adviseCall(buildRequest("What is RAG?"), chain);

        verify(chain).nextCall(any());
        assertThat(response).isSameAs(chainResponse);
    }

    @Test
    @DisplayName("Unsafe input: chain is NOT called, blocked response returned")
    void unsafeInput_chainNotCalled_blockedResponse() {
        when(guardrailService.checkInput(anyString()))
                .thenReturn(GuardrailResult.unsafe("S10: Hate"));

        ChatClientResponse response = advisor.adviseCall(buildRequest("offensive text"), chain);

        verify(chain, never()).nextCall(any());
        String content = response.chatResponse().getResult().getOutput().getText();
        assertThat(content).contains("cannot process");
    }

    @Test
    @DisplayName("Unsafe input: logFlag called with INPUT/BLOCK")
    void unsafeInput_logFlagCalled() {
        when(guardrailService.checkInput(anyString()))
                .thenReturn(GuardrailResult.unsafe("S1: Violent Crimes"));

        advisor.adviseCall(buildRequest("harm"), chain);

        verify(guardrailService).logFlag(null, "INPUT", "S1: Violent Crimes", "BLOCK", "harm");
    }

    @Test
    @DisplayName("Safe input: logFlag is NOT called")
    void safeInput_logFlagNotCalled() {
        when(guardrailService.checkInput(anyString())).thenReturn(GuardrailResult.safe());

        advisor.adviseCall(buildRequest("What is RAG?"), chain);

        verify(guardrailService, never()).logFlag(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getName returns InputGuardrailAdvisor")
    void getName_returnsCorrectName() {
        assertThat(advisor.getName()).isEqualTo("InputGuardrailAdvisor");
    }

    @Test
    @DisplayName("getOrder returns HIGHEST_PRECEDENCE")
    void getOrder_highestPrecedence() {
        assertThat(advisor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
