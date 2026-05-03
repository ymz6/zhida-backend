package org.ymz.app.monitoring;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.service.LlmCallLogService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LlmCallLogChatModelListenerTest {

    @Test
    void recordsSuccessfulCallWithContextAndTokenUsage() {
        LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
        LlmCallLogChatModelListener listener = new LlmCallLogChatModelListener(llmCallLogService);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        attributes.put(LlmMonitoringAttributes.SCENARIO, LlmMonitoringAttributes.SCENARIO_CODE_GENERATION_CREATE);
        attributes.put(LlmMonitoringAttributes.APP_ID, 1L);
        attributes.put(LlmMonitoringAttributes.TASK_ID, 2L);
        ChatRequest request = request("deepseek-v4-pro");

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attributes));
        listener.onResponse(new ChatModelResponseContext(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from("ok"))
                        .modelName("deepseek-v4-pro")
                        .tokenUsage(new TokenUsage(10, 20, 30))
                        .build(),
                request,
                ModelProvider.OPEN_AI,
                attributes
        ));

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(llmCallLogService).save(captor.capture());
        LlmCallLog log = captor.getValue();
        assertEquals(LlmMonitoringAttributes.SCENARIO_CODE_GENERATION_CREATE, log.getScenario());
        assertEquals("deepseek-v4-pro", log.getModelName());
        assertEquals(1L, log.getAppId());
        assertEquals(2L, log.getTaskId());
        assertEquals("SUCCESS", log.getStatus());
        assertEquals(10L, log.getPromptTokens());
        assertEquals(20L, log.getCompletionTokens());
        assertEquals(30L, log.getTotalTokens());
        assertNotNull(log.getDurationMillis());
        assertNotNull(log.getCreatedAt());
    }

    @Test
    void recordsSuccessWhenTokenUsageIsMissing() {
        LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
        LlmCallLogChatModelListener listener = new LlmCallLogChatModelListener(llmCallLogService);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatRequest request = request("qwen3.6-flash");

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attributes));
        listener.onResponse(new ChatModelResponseContext(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from("ok"))
                        .modelName("qwen3.6-flash")
                        .build(),
                request,
                ModelProvider.OPEN_AI,
                attributes
        ));

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(llmCallLogService).save(captor.capture());
        LlmCallLog log = captor.getValue();
        assertEquals(LlmMonitoringAttributes.SCENARIO_TITLE_GENERATION, log.getScenario());
        assertEquals("SUCCESS", log.getStatus());
        assertNull(log.getPromptTokens());
        assertNull(log.getCompletionTokens());
        assertNull(log.getTotalTokens());
    }

    @Test
    void recordsFailedCallWithoutTokenUsage() {
        LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
        LlmCallLogChatModelListener listener = new LlmCallLogChatModelListener(llmCallLogService);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        attributes.put(LlmMonitoringAttributes.SCENARIO, LlmMonitoringAttributes.SCENARIO_CODE_GENERATION_REPAIR);
        ChatRequest request = request("deepseek-v4-pro");

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attributes));
        listener.onError(new ChatModelErrorContext(
                new IllegalStateException("provider failed"),
                request,
                ModelProvider.OPEN_AI,
                attributes
        ));

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(llmCallLogService).save(captor.capture());
        LlmCallLog log = captor.getValue();
        assertEquals(LlmMonitoringAttributes.SCENARIO_CODE_GENERATION_REPAIR, log.getScenario());
        assertEquals("deepseek-v4-pro", log.getModelName());
        assertEquals("FAILED", log.getStatus());
        assertEquals("provider failed", log.getErrorMessage());
    }

    @Test
    void swallowsPersistenceFailure() {
        LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
        doThrow(new RuntimeException("db down")).when(llmCallLogService).save(any(LlmCallLog.class));
        LlmCallLogChatModelListener listener = new LlmCallLogChatModelListener(llmCallLogService);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatRequest request = request("deepseek-v4-pro");

        assertDoesNotThrow(() -> listener.onError(new ChatModelErrorContext(
                new IllegalStateException("provider failed"),
                request,
                ModelProvider.OPEN_AI,
                attributes
        )));
    }

    private ChatRequest request(String modelName) {
        return ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(DefaultChatRequestParameters.builder().modelName(modelName).build())
                .build();
    }
}
