package org.ymz.app.monitoring;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.codegen.runtime.CodeGenerationContext;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.service.LlmCallLogService;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录代码生成 AI Service 的调用明细。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class CodeGenerationAiServiceObservability {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int ERROR_MESSAGE_LIMIT = 1_000;

    private final ConcurrentHashMap<String, Long> requestStartNanos = new ConcurrentHashMap<>();
    private final LlmCallLogService llmCallLogService;

    public AiServiceListener<AiServiceRequestIssuedEvent> requestIssuedListener() {
        return new AiServiceListener<>() {
            @Override
            public Class<AiServiceRequestIssuedEvent> getEventClass() {
                return AiServiceRequestIssuedEvent.class;
            }

            @Override
            public void onEvent(AiServiceRequestIssuedEvent event) {
                requestStartNanos.put(requestKey(event), System.nanoTime());
            }
        };
    }

    public AiServiceListener<AiServiceResponseReceivedEvent> responseReceivedListener() {
        return new AiServiceListener<>() {
            @Override
            public Class<AiServiceResponseReceivedEvent> getEventClass() {
                return AiServiceResponseReceivedEvent.class;
            }

            @Override
            public void onEvent(AiServiceResponseReceivedEvent event) {
                CodeGenerationContext context = codeGenerationContext(event);
                if (context == null) {
                    return;
                }
                String key = requestKey(event);
                Long startNanos = requestStartNanos.remove(key);
                saveLog(
                        context,
                        STATUS_SUCCESS,
                        event.response().modelName(),
                        event.response().tokenUsage() == null ? null : event.response().tokenUsage().inputTokenCount(),
                        event.response().tokenUsage() == null ? null : event.response().tokenUsage().outputTokenCount(),
                        event.response().tokenUsage() == null ? null : event.response().tokenUsage().totalTokenCount(),
                        durationMillis(startNanos),
                        null);
            }
        };
    }

    public AiServiceListener<AiServiceErrorEvent> errorListener() {
        return new AiServiceListener<>() {
            @Override
            public Class<AiServiceErrorEvent> getEventClass() {
                return AiServiceErrorEvent.class;
            }

            @Override
            public void onEvent(AiServiceErrorEvent event) {
                CodeGenerationContext context = codeGenerationContext(event);
                if (context == null) {
                    return;
                }
                Long startNanos = requestStartNanos.remove(requestKey(event));
                saveLog(
                        context,
                        STATUS_FAILED,
                        null,
                        null,
                        null,
                        null,
                        durationMillis(startNanos),
                        event.error() == null ? null : event.error().getMessage());
            }
        };
    }

    private void saveLog(
            CodeGenerationContext context,
            String status,
            String modelName,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Long durationMillis,
            String errorMessage) {
        try {
            llmCallLogService.save(LlmCallLog.builder()
                    .scenario(context.getScenario().getMonitoringScenario())
                    .modelName(modelName)
                    .appId(context.getAppId())
                    .taskId(context.getTaskId())
                    .status(status)
                    .promptTokens(toLong(promptTokens))
                    .completionTokens(toLong(completionTokens))
                    .totalTokens(toLong(totalTokens))
                    .durationMillis(durationMillis)
                    .errorMessage(limit(errorMessage))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception ignored) {
            // 监控失败不影响主流程
        }
    }

    private CodeGenerationContext codeGenerationContext(dev.langchain4j.observability.api.event.AiServiceEvent event) {
        if (event.invocationContext() == null || event.invocationContext().invocationParameters() == null) {
            return null;
        }
        return event.invocationContext().invocationParameters().get("codegenContext");
    }

    private String requestKey(dev.langchain4j.observability.api.event.AiServiceEvent event) {
        return event.invocationContext().invocationId() + ":" + event.invocationContext().methodName()
                + ":" + Objects.hashCode(event.invocationContext().userMessage());
    }

    private Long durationMillis(Long startNanos) {
        if (startNanos == null) {
            return null;
        }
        long duration = (System.nanoTime() - startNanos) / 1_000_000L;
        return Math.max(duration, 0);
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private String limit(String value) {
        if (StrUtil.isBlank(value) || value.length() <= ERROR_MESSAGE_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_MESSAGE_LIMIT);
    }
}
