package org.ymz.app.ai.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ymz.app.ai.listener.LlmLogAiServiceListenerRegistry;
import org.ymz.app.ai.services.CodeGenerateAiService;
import org.ymz.app.ai.services.TitleGenerateAiService;
import org.ymz.app.ai.tools.AiToolRegistry;

/**
 * AI Service 工厂配置
 * @author ymz
 */
@Slf4j
@Configuration
public class AiServiceFactoryConfig {

    /**
     * 标题生成助手
     */
    @Bean
    TitleGenerateAiService titleGenerateAssistant(ChatModel titleGenerateModel, LlmLogAiServiceListenerRegistry llmLogAiServiceListenerRegistry) {
        return AiServices.builder(TitleGenerateAiService.class)
                .chatModel(titleGenerateModel)
                .registerListeners(llmLogAiServiceListenerRegistry.getAiServiceListeners())
                .build();
    }

    /**
     * 代码生成助手
     * 需要按照 appId 隔离，在 LangChain4j 中怎么处理？
     */
    @Bean
    CodeGenerateAiService codeGenerateAssistant(StreamingChatModel codeGenerateModel, RedisChatMemoryStore redisChatMemoryStore, AiToolRegistry aiToolRegistry, LlmLogAiServiceListenerRegistry llmLogAiServiceListenerRegistry) {
        // TODO 假如 Redis 缓存中的对话记忆没了，怎么恢复某一次 MemoryId 的对话？
        // 系统会将 AI 用户的对话信息存储到 MySQL 中，如何从 MySQL 中读取并恢复历史对话记忆呢？
        // 暂时先不考虑吧？我不知道 LangChain4j 怎么做。反正现在暂时还碰不到这个问题
        // 后面再看如何解决，先看效果，再去完善
        return AiServices.builder(CodeGenerateAiService.class)
                .streamingChatModel(codeGenerateModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .chatMemoryStore(redisChatMemoryStore)
                        // TODO 我也不确定最大消息数为多少合适，先调大一些，保证效果，不考虑成本
                        .maxMessages(100)
                        // TODO 这个是什么策略？
//                        .dynamicMaxMessages(new Function<Object, Integer>() {
//                        })
                        .build())
                .tools(aiToolRegistry.allTools())
                // 处理 AI 调用工具时的幻觉问题：AI 可能会幻想一个不存在的工具名称
                .hallucinatedToolNameStrategy(toolExecutionRequest -> {
                    log.warn("AI 出现了工具调用幻觉，其请求了不存在的工具：{}", toolExecutionRequest.name());
                    return ToolExecutionResultMessage.from(toolExecutionRequest, "错误：这里没有任何叫做 %s 的工具，请重新选择！".formatted(toolExecutionRequest.name()));
                })
                .registerListeners(llmLogAiServiceListenerRegistry.getAiServiceListeners())
                .build();
    }
}
