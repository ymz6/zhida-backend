package org.ymz.app.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ymz.app.ai.service.TitleGenerateAssistant;

/**
 * AI Service 工厂配置
 * @author ymz
 */
@Configuration
public class AiServiceFactoryConfig {

    /**
     * 标题生成助手
     */
    @Bean
    TitleGenerateAssistant titleGenerateAssistant(ChatModel titleGenerateModel) {
        return AiServices.builder(TitleGenerateAssistant.class)
                .chatModel(titleGenerateModel)
                .build();
    }
}
