package org.ymz.app.ai.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
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
public class LLMsConfig {

    /**
     * 标题生成模型
     */
    @Bean
    public ChatModel titleGenerateModel() {
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
                .build();
    }

    /**
     * 代码生成模型，本 Agent 应用的大脑
     */
    @Bean
    public StreamingChatModel codeGenerateModel() {
        final String API_KEY = System.getenv("ZHIDA_CODE_GEN_API_KEY");
        if (StrUtil.isBlank(API_KEY)) {
            throw new IllegalStateException("未检测到环境变量 ZHIDA_CODE_GEN_API_KEY，请先在操作系统中设置");
        }

        return OpenAiStreamingChatModel.builder()
                .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                .apiKey(API_KEY)
                .modelName("glm-4.7")
                .temperature(0.1)
                .maxTokens(16384)
                .timeout(Duration.ofMinutes(30))
                .customParameters(Map.of(
                        // 暂时禁用 GLM 的思考模式，后续会考虑打开
                        "thinking", Map.of("type", "disabled")))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
