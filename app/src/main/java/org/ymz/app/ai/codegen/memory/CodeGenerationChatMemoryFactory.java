package org.ymz.app.ai.codegen.memory;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 创建按应用隔离的短期工作记忆窗口。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class CodeGenerationChatMemoryFactory {

    static final int MAX_MESSAGES = 80;
    static final Duration ACTIVE_TTL = Duration.ofDays(7);

    private final ChatMemoryStore codeGenerationChatMemoryStore;

    public dev.langchain4j.memory.ChatMemory create(Long appId) {
        return wrap(MessageWindowChatMemory.builder()
                .id(memoryId(appId))
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(codeGenerationChatMemoryStore)
                .alwaysKeepSystemMessageFirst(true)
                .build());
    }

    public ChatMemoryProvider provider() {
        return memoryId -> wrap(MessageWindowChatMemory.builder()
                .id(normalizeMemoryId(memoryId))
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(codeGenerationChatMemoryStore)
                .alwaysKeepSystemMessageFirst(true)
                .build());
    }

    String memoryId(Long appId) {
        if (appId == null) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        return "app:" + appId;
    }

    private String normalizeMemoryId(Object memoryId) {
        if (memoryId == null) {
            throw new IllegalArgumentException("memoryId 不能为空");
        }
        String value = String.valueOf(memoryId);
        return value.startsWith("app:") ? value : "app:" + value;
    }

    private dev.langchain4j.memory.ChatMemory wrap(dev.langchain4j.memory.ChatMemory memory) {
        return new ActiveTtlChatMemory(memory, ACTIVE_TTL);
    }
}
