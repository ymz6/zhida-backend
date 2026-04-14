package org.ymz.app.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                .temperature(0.3)
                .maxCompletionTokens(2048)
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
                .temperature(0.3)
                .maxCompletionTokens(2048)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

}
