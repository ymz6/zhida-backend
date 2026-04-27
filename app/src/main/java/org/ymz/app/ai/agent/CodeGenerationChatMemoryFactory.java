package org.ymz.app.ai.agent;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 创建按应用隔离的 CodeGenerationAgent 记忆窗口。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class CodeGenerationChatMemoryFactory {

    static final int MAX_MESSAGES = 80;

    private final ChatMemoryStore codeGenerationChatMemoryStore;

    public ChatMemory create(Long appId) {
        return MessageWindowChatMemory.builder()
                .id(memoryId(appId))
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(codeGenerationChatMemoryStore)
                .alwaysKeepSystemMessageFirst(true)
                .build();
    }

    String memoryId(Long appId) {
        if (appId == null) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        return "app:" + appId;
    }
}
