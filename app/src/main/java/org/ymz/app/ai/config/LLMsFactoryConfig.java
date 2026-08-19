package org.ymz.app.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * LLM 配置
 * 
 * @author ymz
 */
@Configuration
public class LLMsFactoryConfig {

    @ConfigurationProperties(prefix = "zhida.llm")
    public record Properties(String titleGenApiKey, String codeGenApiKey) {}

    /**
     * 标题生成模型
     */
    @Bean
    public ChatModel titleGenerateModel(LLMsFactoryConfig.Properties properties) {
        return OpenAiChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .modelName("qwen3.6-flash")
                .apiKey(API_KEY)
                .apiKey(properties.titleGenApiKey)
                .temperature(0.3)
                // 结构化输出
                .responseFormat(ResponseFormat.JSON)
                .customParameters(
                        // 禁用 Qwen 的思考模式
                        Map.of("enable_thinking", false))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 代码生成模型
     */
    @Bean
    public StreamingChatModel codeGenerateModel(LLMsFactoryConfig.Properties properties) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(properties.codeGenApiKey)
                .modelName("deepseek-v4-pro")
                .temperature(0.1)
                .maxTokens(16384)
                // DeepSeek 思考模式在工具调用场景要求后续请求完整回传思考内容。
                .returnThinking(true)
                .sendThinking(true)
                .accumulateToolCallId(false)
                .timeout(Duration.ofMinutes(30))
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
