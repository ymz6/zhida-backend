package org.ymz.app.ai.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.listener.AiServiceErrorListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceRequestIssuedListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ymz.app.model.entity.LlmLog;
import org.ymz.app.model.enums.monitoring.LlmStatus;
import org.ymz.app.service.LlmLogService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AiService 监听器注册表
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmLogAiServiceListenerRegistry {

    private static final String PARAM_USER_ID = "userId";
    private final ObjectMapper objectMapper;

    @Getter
    private final List<AiServiceListener<?>> aiServiceListeners = List.of(
            new RequestIssuedListener(),
            new ResponseReceivedListener(),
            new ErrorListener()
    );

    /**
     * 记录每一次底层 LLM 请求的开始信息。
     * key 使用 ChatRequest，因为 RequestIssuedEvent 和 ResponseReceivedEvent 都会携带对应 request。
     */
    private final ConcurrentHashMap<ChatRequest, RequestTrace> requestTraceMap = new ConcurrentHashMap<>();
    private final LlmLogService llmLogService;

    private record RequestTrace(UUID invocationId, Long userId, long startTimeMillis) {}

    // 每一个内部类对应一个 AiService 事件的监听器
    /**
     * 每次底层 LLM 请求发出前触发。
     */
    public class RequestIssuedListener implements AiServiceRequestIssuedListener {

        @Override
        public void onEvent(AiServiceRequestIssuedEvent event) {
            InvocationContext context = event.invocationContext();
            InvocationParameters parameters = context.invocationParameters();
            RequestTrace trace = new RequestTrace(
                    context.invocationId(),
                    Long.valueOf(parameters.get(LlmLogAiServiceListenerRegistry.PARAM_USER_ID).toString()),
                    System.currentTimeMillis()
            );
            requestTraceMap.put(event.request(), trace);
        }
    }

    /**
     * 每次底层 LLM 成功返回后触发。
     */
    public class ResponseReceivedListener implements AiServiceResponseReceivedListener {

        @Override
        public void onEvent(AiServiceResponseReceivedEvent event) {
            InvocationContext context = event.invocationContext();
            RequestTrace trace = requestTraceMap.remove(event.request());
            Long durationMillis = System.currentTimeMillis() - trace.startTimeMillis();
            Long userId = trace.userId();
            ChatResponse chatResponse = event.response();
            TokenUsage tokenUsage = chatResponse.tokenUsage();

            String usageRawJson = null;
            OpenAiChatResponseMetadata metadata = (OpenAiChatResponseMetadata) chatResponse.metadata();
            if (metadata.rawHttpResponse() != null && metadata.rawHttpResponse().body() != null) {
                try {
                    JsonNode rootNode = objectMapper.readTree(metadata.rawHttpResponse().body());
                    JsonNode usageNode = rootNode.get("usage");
                    if (usageNode != null && !usageNode.isNull()) {
                        usageRawJson = objectMapper.writeValueAsString(usageNode);
                    }
                } catch (Exception e) {
                    log.warn("提取 LLM usage 原始 JSON 失败", e);
                }
            }
            // 构建 LLMLog
            LlmLog llmLog = LlmLog.builder()
                    .modelName(chatResponse.modelName())
                    .userId(userId)
                    .status(LlmStatus.SUCCESS.name())
                    .durationMillis(durationMillis)
                    .inputTokens(tokenUsage.inputTokenCount().longValue())
                    .outputTokens(tokenUsage.outputTokenCount().longValue())
                    .totalTokens(tokenUsage.totalTokenCount().longValue())
                    .usageJson(usageRawJson)
                    .build();
            llmLogService.save(llmLog);
        }
    }

    /**
     * AiService 调用失败时触发。
     */
    public  class ErrorListener implements AiServiceErrorListener {

        @Override
        public void onEvent(AiServiceErrorEvent event) {
            InvocationContext context = event.invocationContext();
            InvocationParameters parameters = context.invocationParameters();

            // 失败时不计算耗时，但要清理该 invocation 下残留的 request trace。
            requestTraceMap.entrySet().removeIf(entry ->
                    context.invocationId().equals(entry.getValue().invocationId())
            );

            LlmLog llmLog = LlmLog.builder()
                    .userId(parameters.get(LlmLogAiServiceListenerRegistry.PARAM_USER_ID))
                    .status(LlmStatus.FAILED.name())
                    .durationMillis(null)
                    .errorMessage(event.error().getMessage())
                    .build();
            llmLogService.save(llmLog);
        }
    }
}
