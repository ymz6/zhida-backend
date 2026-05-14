package org.ymz.app.ai.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ymz.app.model.entity.LlmLog;
import org.ymz.app.model.enums.monitoring.LlmStatus;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.service.LlmLogService;

/**
 * LLM 日志监听器
 * 
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMLogListener implements ChatModelListener {

    private static final String START_TIME_KEY = "llm-log-start-time";
    private static final String USER_ID_KEY = "llm-log-user-id";

    private final LlmLogService llmLogService;
    private final ObjectMapper objectMapper;

    /*
     * Notes:
     * 非流式 ChatModel：一般同线程，但也不要依赖 ThreadLocal。
     * 流式 StreamingChatModel：onRequest 和 onResponse/onError 明确可能不同线程。
     * 这里统一通过LangChain4j提供的 context 来在不同的回调之间传递数据
     */

    /**
     * 请求 LLM 前的回调
     */
    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 记录请求开始时间、请求参数等
        Long userId = AuthContextHolder.get().getUserId();
        Long startTime = System.currentTimeMillis();

        requestContext.attributes().put(USER_ID_KEY, userId);
        requestContext.attributes().put(START_TIME_KEY, startTime);
    }

    /**
     * LLM 成功返回完整响应后的回调。
     * 注意：对于 StreamingChatModel，该方法不是流式片段回调，
     * 而是在完整响应形成后调用，并且早于 StreamingChatResponseHandler.onCompleteResponse()。
     */
    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Long userId = (Long) responseContext.attributes().get(USER_ID_KEY);
        Long startTime = (Long) responseContext.attributes().get(START_TIME_KEY);
        Long endTime = System.currentTimeMillis();

        ChatResponse chatResponse = responseContext.chatResponse();
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
                .durationMillis(endTime - startTime)
                .inputTokens(tokenUsage.inputTokenCount().longValue())
                .outputTokens(tokenUsage.outputTokenCount().longValue())
                .totalTokens(tokenUsage.totalTokenCount().longValue())
                .usageJson(usageRawJson)
                .build();
        llmLogService.save(llmLog);
    }

    /**
     * 调用发生错误时回调
     */
    @Override
    public void onError(ChatModelErrorContext errorContext) {
        log.warn("LLM 调用失败", errorContext.error());
        Long userId = (Long) errorContext.attributes().get(USER_ID_KEY);

        ChatRequest chatRequest = errorContext.chatRequest();
        // 构建 LLMLog
        LlmLog llmLog = LlmLog.builder()
                .modelName(chatRequest.modelName())
                .userId(userId)
                .status(LlmStatus.FAILED.name())
                .errorMessage(errorContext.error().getMessage())
                .build();
        llmLogService.save(llmLog);
    }
}
