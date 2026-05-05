package org.ymz.app.monitoring;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.service.LlmCallLogService;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记录 LLM 调用明细，避免保存 prompt/response 原文。
 *
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmCallLogChatModelListener implements ChatModelListener {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int ERROR_MESSAGE_LIMIT = 1000;

    private final LlmCallLogService llmCallLogService;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().putIfAbsent(LlmMonitoringAttributes.START_NANOS, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();
        saveLog(
                responseContext.attributes(),
                responseContext.chatRequest(),
                responseContext.chatResponse().modelName(),
                STATUS_SUCCESS,
                tokenUsage == null ? null : tokenUsage.inputTokenCount(),
                tokenUsage == null ? null : tokenUsage.outputTokenCount(),
                tokenUsage == null ? null : tokenUsage.totalTokenCount(),
                null
        );
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        saveLog(
                errorContext.attributes(),
                errorContext.chatRequest(),
                null,
                STATUS_FAILED,
                null,
                null,
                null,
                errorContext.error().getMessage()
        );
    }

    private void saveLog(
            Map<Object, Object> attributes,
            ChatRequest chatRequest,
            String responseModelName,
            String status,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String errorMessage
    ) {
        try {
            String modelName = StrUtil.blankToDefault(responseModelName, requestModelName(chatRequest));
            if (shouldSkip(modelName, attributes)) {
                return;
            }
            LlmCallLog log = LlmCallLog.builder()
                    .scenario(resolveScenario(attributes, modelName))
                    .modelName(modelName)
                    .appId(toLong(attributes.get(LlmMonitoringAttributes.APP_ID)))
                    .taskId(toLong(attributes.get(LlmMonitoringAttributes.TASK_ID)))
                    .status(status)
                    .promptTokens(toLong(promptTokens))
                    .completionTokens(toLong(completionTokens))
                    .totalTokens(toLong(totalTokens))
                    .durationMillis(durationMillis(attributes))
                    .errorMessage(limit(errorMessage))
                    .createdAt(LocalDateTime.now())
                    .build();
            llmCallLogService.save(log);
        } catch (Exception e) {
            log.warn("记录 LLM 调用明细失败", e);
        }
    }

    private boolean shouldSkip(String modelName, Map<Object, Object> attributes) {
        if (!"deepseek-v4-pro".equals(modelName)) {
            return false;
        }
        Object scenario = attributes.get(LlmMonitoringAttributes.SCENARIO);
        return scenario == null || StrUtil.isBlank(String.valueOf(scenario));
    }

    private String requestModelName(ChatRequest chatRequest) {
        if (chatRequest == null || chatRequest.parameters() == null) {
            return null;
        }
        return chatRequest.parameters().modelName();
    }

    private String resolveScenario(Map<Object, Object> attributes, String modelName) {
        Object scenario = attributes.get(LlmMonitoringAttributes.SCENARIO);
        if (scenario != null && StrUtil.isNotBlank(String.valueOf(scenario))) {
            return String.valueOf(scenario);
        }
        if ("qwen3.6-flash".equals(modelName)) {
            return LlmMonitoringAttributes.SCENARIO_TITLE_GENERATION;
        }
        return "UNKNOWN";
    }

    private Long durationMillis(Map<Object, Object> attributes) {
        Object start = attributes.get(LlmMonitoringAttributes.START_NANOS);
        if (!(start instanceof Number startNanos)) {
            return null;
        }
        long duration = (System.nanoTime() - startNanos.longValue()) / 1_000_000L;
        return Math.max(duration, 0);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String limit(String value) {
        if (value == null || value.length() <= ERROR_MESSAGE_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_MESSAGE_LIMIT);
    }
}
