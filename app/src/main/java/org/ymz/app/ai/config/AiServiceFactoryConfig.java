package org.ymz.app.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ymz.app.ai.services.TitleGenerateAiService;

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
    TitleGenerateAiService titleGenerateAssistant(ChatModel titleGenerateModel) {
        return AiServices.builder(TitleGenerateAiService.class)
                .chatModel(titleGenerateModel)
                .build();
    }
}
