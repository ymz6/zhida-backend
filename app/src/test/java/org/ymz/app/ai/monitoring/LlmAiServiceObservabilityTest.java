package org.ymz.app.ai.monitoring;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.model.enums.monitoring.LlmCallStatus;
import org.ymz.app.service.LlmCallLogService;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LlmAiServiceObservabilityTest {

    @Test
    void recordsSuccessfulCallWithOpenAiTokenDetails() {
        LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
        LlmAiServiceObservability observability = new LlmAiServiceObservability(llmCallLogService);
        InvocationContext context = invocationContext(new LlmMonitoringContext("CODE_GENERATION_CREATE", 1L, 2L));
        ChatRequest request = request();
        ChatResponse response = response();

        observability.requestIssuedListener().onEvent(AiServiceRequestIssuedEvent.builder()
                .invocationContext(context)
                .request(request)
                .build());
        observability.responseReceivedListener().onEvent(AiServiceResponseReceivedEvent.builder()
                .invocationContext(context)
                .request(request)
                .response(response)
                .build());

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(llmCallLogService).save(captor.capture());
        LlmCallLog log = captor.getValue();
        assertEquals("CODE_GENERATION_CREATE", log.getScenario());
        assertEquals("deepseek-v4-pro", log.getModelName());
        assertEquals("chatcmpl_1", log.getResponseId());
        assertEquals("STOP", log.getFinishReason());
        assertEquals(1L, log.getAppId());
        assertEquals(2L, log.getTaskId());
        assertEquals(LlmCallStatus.SUCCESS.name(), log.getStatus());
        assertEquals(100L, log.getInputTokens());
        assertEquals(50L, log.getOutputTokens());
        assertEquals(150L, log.getTotalTokens());
        assertNotNull(log.getDurationMillis());
        assertNotNull(log.getCreatedAt());
    }

    @Test
    void recordsMissingTokenUsageAsZero() {
        LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
        LlmAiServiceObservability observability = new LlmAiServiceObservability(llmCallLogService);
        InvocationContext context = invocationContext(new LlmMonitoringContext("CODE_GENERATION_CREATE", 1L, 2L));
        ChatRequest request = request();
        ChatResponse response = responseWithoutRawUsage();

        observability.requestIssuedListener().onEvent(AiServiceRequestIssuedEvent.builder()
                .invocationContext(context)
                .request(request)
                .build());
        observability.responseReceivedListener().onEvent(AiServiceResponseReceivedEvent.builder()
                .invocationContext(context)
                .request(request)
                .response(response)
                .build());

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(llmCallLogService).save(captor.capture());
        LlmCallLog log = captor.getValue();
        assertEquals(0L, log.getInputTokens());
        assertEquals(0L, log.getOutputTokens());
        assertEquals(0L, log.getTotalTokens());
    }

    @Test
    void recordsFailedCallWithoutTokenUsage() {
        LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
        LlmAiServiceObservability observability = new LlmAiServiceObservability(llmCallLogService);
        InvocationContext context = invocationContext(new LlmMonitoringContext("TITLE_GENERATION", null, null));
        ChatRequest request = request();

        observability.requestIssuedListener().onEvent(AiServiceRequestIssuedEvent.builder()
                .invocationContext(context)
                .request(request)
                .build());
        observability.errorListener().onEvent(AiServiceErrorEvent.builder()
                .invocationContext(context)
                .error(new IllegalStateException("provider failed"))
                .build());

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(llmCallLogService).save(captor.capture());
        LlmCallLog log = captor.getValue();
        assertEquals("TITLE_GENERATION", log.getScenario());
        assertEquals("deepseek-v4-pro", log.getModelName());
        assertEquals(LlmCallStatus.FAILED.name(), log.getStatus());
        assertEquals(0L, log.getInputTokens());
        assertEquals(0L, log.getOutputTokens());
        assertEquals(0L, log.getTotalTokens());
        assertEquals(IllegalStateException.class.getName(), log.getErrorType());
        assertEquals("provider failed", log.getErrorMessage());
    }

    @Test
    void swallowsPersistenceFailure() {
        LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
        doThrow(new RuntimeException("db down")).when(llmCallLogService).save(any(LlmCallLog.class));
        LlmAiServiceObservability observability = new LlmAiServiceObservability(llmCallLogService);
        InvocationContext context = invocationContext(new LlmMonitoringContext("TITLE_GENERATION", null, null));

        assertDoesNotThrow(() -> observability.errorListener().onEvent(AiServiceErrorEvent.builder()
                .invocationContext(context)
                .error(new IllegalStateException("provider failed"))
                .build()));
    }

    private InvocationContext invocationContext(LlmMonitoringContext monitoringContext) {
        return InvocationContext.builder()
                .invocationId(UUID.randomUUID())
                .methodName("chat")
                .invocationParameters(InvocationParameters.from(Map.of(LlmMonitoringAttributes.CONTEXT,
                        monitoringContext)))
                .timestampNow()
                .build();
    }

    private ChatRequest request() {
        return ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(OpenAiChatRequestParameters.builder()
                        .modelName("deepseek-v4-pro")
                        .build())
                .build();
    }

    private ChatResponse response() {
        OpenAiTokenUsage usage = OpenAiTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .totalTokenCount(150)
                .build();
        return response(usage);
    }

    private ChatResponse response(OpenAiTokenUsage usage) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .metadata(OpenAiChatResponseMetadata.builder()
                        .id("chatcmpl_1")
                        .modelName("deepseek-v4-pro")
                        .tokenUsage(usage)
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();
    }

    private ChatResponse responseWithoutRawUsage() {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .metadata(OpenAiChatResponseMetadata.builder()
                        .id("chatcmpl_1")
                        .modelName("deepseek-v4-pro")
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();
    }
}
