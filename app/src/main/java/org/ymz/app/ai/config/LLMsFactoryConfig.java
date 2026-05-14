package org.ymz.app.ai.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ymz.app.ai.listener.LLMLogListener;

import java.time.Duration;
import java.util.Map;

/**
 * LLM 配置
 * 
 * @author ymz
 */
@Configuration
public class LLMsFactoryConfig {

    /**
     * 标题生成模型
     */
    @Bean
    public ChatModel titleGenerateModel(LLMLogListener llmLogListener) {
        final String API_KEY = System.getenv("ZHIDA_TITLE_GEN_API_KEY");
        if (StrUtil.isBlank(API_KEY)) {
            throw new IllegalStateException("未检测到环境变量 ZHIDA_TITLE_GEN_API_KEY，请先在操作系统中设置");
        }
        return OpenAiChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .modelName("qwen3.6-flash")
                .apiKey(API_KEY)
                .temperature(0.3)
                // 结构化输出
                .responseFormat(ResponseFormat.JSON)
                .customParameters(
                        // 禁用 Qwen 的思考模式
                        Map.of("enable_thinking", false))
                .logRequests(true)
                .logResponses(true)
                .listeners(llmLogListener)
                .build();
    }

    /**
     * 代码生成模型
     */
    @Bean
    public StreamingChatModel codeGenerateModel(LLMLogListener llmLogListener) {
        final String API_KEY = System.getenv("ZHIDA_CODE_GEN_API_KEY");
        if (StrUtil.isBlank(API_KEY)) {
            throw new IllegalStateException("未检测到环境变量 ZHIDA_CODE_GEN_API_KEY，请先在操作系统中设置");
        }
        return OpenAiStreamingChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(API_KEY)
                .modelName("deepseek-v4-pro")
                .temperature(0.1)
                .maxTokens(16384)
                .timeout(Duration.ofMinutes(30))
                .customParameters(Map.of(
                        // 暂时禁用思考模式，后续会考虑打开
                        "thinking", Map.of("type", "disabled")))
                .listeners(llmLogListener)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
