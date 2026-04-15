package org.ymz.app.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * LangChain4j 全局配置
 * @author ymz
 */
@Configuration
public class LangChain4jConfig {
    private static final String MODEL_NAME = "glm-4.7";
    private static final String BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    private static final String API_KEY;

    static {
        String apiKey = System.getenv("ZHIDA_API_KEY");
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("未检测到环境变量 ZHIDA_API_KEY，请先在操作系统中设置");
        }
        API_KEY = apiKey;
    }

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .temperature(0.1)
                .maxTokens(8192)
                .timeout(Duration.ofSeconds(180))
                .customParameters(Map.of(
                        // 启用 GLM 的结构化输出能力
                        "response_format", Map.of("type", "json_object"),
                        // 关闭 GLM 的思考模式
                        "thinking", Map.of("type", "disabled")
                ))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .temperature(0.1)
                .maxTokens(8192)
                .timeout(Duration.ofSeconds(180))
                .customParameters(Map.of(
                        // 启用 GLM 的结构化输出能力
                        "response_format", Map.of("type", "json_object"),
                        // 关闭 GLM 的思考模式
                        "thinking", Map.of("type", "disabled")
                ))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

}
