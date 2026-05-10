package org.ymz.app.ai.monitoring;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.codegen.runtime.CodeGenerationContext;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.model.enums.monitoring.LlmCallStatus;
import org.ymz.app.service.LlmCallLogService;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 LangChain4j 官方 AI Service 事件记录 LLM 调用明细。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class LlmAiServiceObservability {

    private static final int ERROR_MESSAGE_LIMIT = 1_000;

    /**
     * 请求发出和响应返回是两个事件，这里临时保存请求与开始时间用于计算耗时。
     */
    private final ConcurrentHashMap<String, RequestRecord> requestRecords = new ConcurrentHashMap<>();
    private final LlmCallLogService llmCallLogService;

    public AiServiceListener<AiServiceRequestIssuedEvent> requestIssuedListener() {
        return new AiServiceListener<>() {
            @Override
            public Class<AiServiceRequestIssuedEvent> getEventClass() {
                return AiServiceRequestIssuedEvent.class;
            }

            @Override
            public void onEvent(AiServiceRequestIssuedEvent event) {
                // 请求发出时先记录上下文，等待后续成功或失败事件来补全日志。
                requestRecords.put(requestKey(event), new RequestRecord(event.request(), System.nanoTime()));
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
                // 成功响应到达后取出请求记录，避免 ConcurrentHashMap 长期累积。
                RequestRecord record = requestRecords.remove(requestKey(event));
                saveSuccess(event, record);
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
                // 失败事件也要清理请求记录，保证异常调用不会留下临时状态。
                RequestRecord record = requestRecords.remove(requestKey(event));
                saveFailure(event, record);
            }
        };
    }

    private void saveSuccess(AiServiceResponseReceivedEvent event, RequestRecord record) {
        LlmMonitoringContext context = monitoringContext(event);
        if (context == null) {
            // 没有业务监控上下文的 AI 调用不落库，避免混入非应用生成场景。
            return;
        }
        ChatRequest request = event.request() == null && record != null ? record.request() : event.request();
        ChatResponse response = event.response();
        TokenUsage usage = response == null ? null : response.tokenUsage();
        // 兼容不同模型服务商，只记录 LangChain4j 抽象出的通用 token 计数。
        saveLog(LlmCallLog.builder()
                .scenario(context.scenario())
                .modelName(modelName(request, response))
                .responseId(response == null ? null : response.id())
                .finishReason(finishReason(response))
                .appId(context.appId())
                .taskId(context.taskId())
                .status(LlmCallStatus.SUCCESS.name())
                .inputTokens(value(usage == null ? null : usage.inputTokenCount()))
                .outputTokens(value(usage == null ? null : usage.outputTokenCount()))
                .totalTokens(value(usage == null ? null : usage.totalTokenCount()))
                .durationMillis(durationMillis(record))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void saveFailure(AiServiceErrorEvent event, RequestRecord record) {
        LlmMonitoringContext context = monitoringContext(event);
        if (context == null) {
            // 没有业务监控上下文时无法关联场景、应用和任务，直接跳过。
            return;
        }
        ChatRequest request = record == null ? null : record.request();
        Throwable error = event.error();
        saveLog(LlmCallLog.builder()
                .scenario(context.scenario())
                .modelName(configuredModelName(request))
                .appId(context.appId())
                .taskId(context.taskId())
                .status(LlmCallStatus.FAILED.name())
                .inputTokens(0L)
                .outputTokens(0L)
                .totalTokens(0L)
                .durationMillis(durationMillis(record))
                .errorType(error == null ? null : error.getClass().getName())
                .errorMessage(limit(error == null ? null : error.getMessage()))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void saveLog(LlmCallLog log) {
        try {
            llmCallLogService.save(log);
        } catch (Exception ignored) {
            // 监控失败不能影响主业务调用。
        }
    }

    private LlmMonitoringContext monitoringContext(AiServiceEvent event) {
        InvocationContext invocationContext = event.invocationContext();
        InvocationParameters parameters = invocationContext == null ? null : invocationContext.invocationParameters();
        if (parameters == null) {
            return null;
        }
        LlmMonitoringContext context = parameters.get(LlmMonitoringAttributes.CONTEXT);
        if (context != null) {
            return context;
        }
        CodeGenerationContext codegenContext = parameters.get("codegenContext");
        if (codegenContext != null) {
            // 兼容旧的代码生成上下文，统一转换成监控模块自己的上下文结构。
            return new LlmMonitoringContext(
                    codegenContext.getScenario().getMonitoringScenario(),
                    codegenContext.getAppId(),
                    codegenContext.getTaskId()
            );
        }
        return null;
    }

    private String requestKey(AiServiceEvent event) {
        InvocationContext context = event.invocationContext();
        if (context == null) {
            // 极端情况下没有 invocationContext，使用固定 key 保证流程不中断。
            return "unknown";
        }
        // 同一次 AI Service 方法调用的请求、响应、异常事件会共享 invocationId 与 methodName。
        return context.invocationId() + ":" + context.methodName();
    }

    private String modelName(ChatRequest request, ChatResponse response) {
        // 优先记录服务商实际返回的模型名；没有返回时退回请求配置的模型名。
        return StrUtil.blankToDefault(response == null ? null : response.modelName(), configuredModelName(request));
    }

    private String configuredModelName(ChatRequest request) {
        if (request == null || request.parameters() == null) {
            return null;
        }
        return request.parameters().modelName();
    }

    private String finishReason(ChatResponse response) {
        FinishReason finishReason = response == null ? null : response.finishReason();
        return finishReason == null ? null : finishReason.name();
    }

    private Long durationMillis(RequestRecord record) {
        if (record == null) {
            // 没拿到请求发出事件时无法准确计算耗时。
            return null;
        }
        long duration = (System.nanoTime() - record.startNanos()) / 1_000_000L;
        return Math.max(duration, 0);
    }

    private long value(Integer value) {
        return value == null ? 0 : value.longValue();
    }

    private String limit(String value) {
        if (value == null || value.length() <= ERROR_MESSAGE_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_MESSAGE_LIMIT);
    }

    private record RequestRecord(ChatRequest request, long startNanos) {
    }
}
