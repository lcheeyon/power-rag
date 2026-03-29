package com.powerrag.guardrails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuardrailService Unit Tests")
class GuardrailServiceTest {

    @Mock ChatClient                geminiGuardClient;
    @Mock ChatClient.ChatClientRequestSpec requestSpec;
    @Mock ChatClient.CallResponseSpec      callSpec;
    @Mock GuardrailFlagRepository   flagRepository;

    GuardrailService service;

    @BeforeEach
    void setUp() {
        service = new GuardrailService(geminiGuardClient, flagRepository, "gemini-2.5-flash");
        // @Value fields are not injected without Spring; default false would skip all LLM calls.
        ReflectionTestUtils.setField(service, "guardrailsEnabled", true);
        // Wire ChatClient fluent chain
        lenient().when(geminiGuardClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.options(any())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callSpec);
    }

    // ── checkInput ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkInput: LLM responds 'safe' → passes")
    void checkInput_safeResponse_passes() {
        when(callSpec.content()).thenReturn("safe");
        assertThat(service.checkInput("What is RAG?").passed()).isTrue();
    }

    @Test
    @DisplayName("checkInput: LLM responds 'unsafe' → blocked with category")
    void checkInput_unsafeResponse_blockedWithCategory() {
        when(callSpec.content()).thenReturn("unsafe\nS10: Hate");
        GuardrailResult result = service.checkInput("offensive text");
        assertThat(result.passed()).isFalse();
        assertThat(result.category()).isEqualTo("S10: Hate");
    }

    @Test
    @DisplayName("checkInput: LLM responds 'unsafe' without category → POLICY_VIOLATION")
    void checkInput_unsafeNoCategory_policyViolation() {
        when(callSpec.content()).thenReturn("unsafe");
        GuardrailResult result = service.checkInput("bad text");
        assertThat(result.passed()).isFalse();
        assertThat(result.category()).isEqualTo("POLICY_VIOLATION");
    }

    @Test
    @DisplayName("checkInput: LLM throws exception → fails open (safe)")
    void checkInput_llmException_failsOpen() {
        when(geminiGuardClient.prompt()).thenThrow(new RuntimeException("Gemini unavailable"));
        assertThat(service.checkInput("test").passed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("checkInput: blank/empty input → safe without calling LLM")
    void checkInput_blank_safeWithoutLlmCall(String input) {
        assertThat(service.checkInput(input).passed()).isTrue();
        verify(geminiGuardClient, never()).prompt();
    }

    @Test
    @DisplayName("checkInput: null input → safe without calling LLM")
    void checkInput_null_safe() {
        assertThat(service.checkInput(null).passed()).isTrue();
        verify(geminiGuardClient, never()).prompt();
    }

    // ── checkOutput ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkOutput: no PII → safe")
    void checkOutput_noPii_safe() {
        assertThat(service.checkOutput("RAG is a retrieval augmented generation system.").passed()).isTrue();
    }

    @Test
    @DisplayName("checkOutput: email in text → PII_EMAIL")
    void checkOutput_email_piiEmail() {
        GuardrailResult r = service.checkOutput("Contact admin@example.com for help.");
        assertThat(r.passed()).isFalse();
        assertThat(r.category()).isEqualTo("PII_EMAIL");
    }

    @Test
    @DisplayName("checkOutput: SSN in text → PII_SSN")
    void checkOutput_ssn_piiSsn() {
        GuardrailResult r = service.checkOutput("SSN is 123-45-6789.");
        assertThat(r.passed()).isFalse();
        assertThat(r.category()).isEqualTo("PII_SSN");
    }

    @Test
    @DisplayName("checkOutput: phone number in text → PII_PHONE")
    void checkOutput_phone_piiPhone() {
        GuardrailResult r = service.checkOutput("Call 555-867-5309 for info.");
        assertThat(r.passed()).isFalse();
        assertThat(r.category()).isEqualTo("PII_PHONE");
    }

    @Test
    @DisplayName("checkOutput: credit card number → PII_CREDIT_CARD")
    void checkOutput_creditCard_piiCreditCard() {
        GuardrailResult r = service.checkOutput("Card: 4111-1111-1111-1111");
        assertThat(r.passed()).isFalse();
        assertThat(r.category()).isEqualTo("PII_CREDIT_CARD");
    }

    @Test
    @DisplayName("checkOutput: null input → safe")
    void checkOutput_null_safe() {
        assertThat(service.checkOutput(null).passed()).isTrue();
    }

    // ── redactPii ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("redactPii: email replaced with token")
    void redactPii_email_replaced() {
        String result = service.redactPii("Contact admin@example.com now.");
        assertThat(result).contains("[EMAIL REDACTED]");
        assertThat(result).doesNotContain("admin@example.com");
    }

    @Test
    @DisplayName("redactPii: SSN replaced with token")
    void redactPii_ssn_replaced() {
        String result = service.redactPii("SSN: 123-45-6789.");
        assertThat(result).contains("[SSN REDACTED]");
        assertThat(result).doesNotContain("123-45-6789");
    }

    @Test
    @DisplayName("redactPii: null input returns null")
    void redactPii_null_returnsNull() {
        assertThat(service.redactPii(null)).isNull();
    }

    // ── logFlag ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logFlag: saves flag with correct fields")
    void logFlag_savesFlag() {
        GuardrailFlag saved = GuardrailFlag.builder()
                .id(UUID.randomUUID()).stage("INPUT").ruleTriggered("S10: Hate")
                .severity("BLOCK").rawContent("bad text").build();
        when(flagRepository.save(any(GuardrailFlag.class))).thenReturn(saved);

        service.logFlag(null, "INPUT", "S10: Hate", "BLOCK", "bad text");

        ArgumentCaptor<GuardrailFlag> captor = ArgumentCaptor.forClass(GuardrailFlag.class);
        verify(flagRepository).save(captor.capture());
        GuardrailFlag flag = captor.getValue();
        assertThat(flag.getStage()).isEqualTo("INPUT");
        assertThat(flag.getRuleTriggered()).isEqualTo("S10: Hate");
        assertThat(flag.getSeverity()).isEqualTo("BLOCK");
        assertThat(flag.getRawContent()).isEqualTo("bad text");
        assertThat(flag.getInteractionId()).isNull();
    }

    @Test
    @DisplayName("logFlag: raw content truncated at 500 chars")
    void logFlag_longContent_truncated() {
        String longText = "x".repeat(600);
        when(flagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.logFlag(null, "OUTPUT", "PII_EMAIL", "WARN", longText);

        ArgumentCaptor<GuardrailFlag> captor = ArgumentCaptor.forClass(GuardrailFlag.class);
        verify(flagRepository).save(captor.capture());
        assertThat(captor.getValue().getRawContent()).hasSize(500);
    }

    @Test
    @DisplayName("logFlag: null ruleTriggered defaults to POLICY_VIOLATION")
    void logFlag_nullRule_defaultsToViolation() {
        when(flagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.logFlag(null, "INPUT", null, "BLOCK", "text");

        ArgumentCaptor<GuardrailFlag> captor = ArgumentCaptor.forClass(GuardrailFlag.class);
        verify(flagRepository).save(captor.capture());
        assertThat(captor.getValue().getRuleTriggered()).isEqualTo("POLICY_VIOLATION");
    }
}
