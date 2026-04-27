package org.ymz.app.ai.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGenerationChatMemoryFactoryTest {

    @Test
    void createsStableAppScopedMemoryId() {
        CodeGenerationChatMemoryFactory factory = new CodeGenerationChatMemoryFactory(new InMemoryChatMemoryStore());

        assertEquals("app:42", factory.memoryId(42L));
    }

    @Test
    void keepsSystemMessageFirst() {
        CodeGenerationChatMemoryFactory factory = new CodeGenerationChatMemoryFactory(new InMemoryChatMemoryStore());
        ChatMemory memory = factory.create(42L);

        memory.add(UserMessage.from("先来的用户需求"));
        memory.add(SystemMessage.from("系统提示词"));

        List<ChatMessage> messages = memory.messages();
        assertTrue(messages.getFirst() instanceof SystemMessage);
        assertTrue(messages.toString().contains("先来的用户需求"));
    }

    @Test
    void cropsMessagesByWindowSize() {
        CodeGenerationChatMemoryFactory factory = new CodeGenerationChatMemoryFactory(new InMemoryChatMemoryStore());
        ChatMemory memory = factory.create(42L);

        for (int index = 0; index < CodeGenerationChatMemoryFactory.MAX_MESSAGES + 5; index++) {
            memory.add(UserMessage.from("msg-" + index));
        }

        List<ChatMessage> messages = memory.messages();
        assertEquals(CodeGenerationChatMemoryFactory.MAX_MESSAGES, messages.size());
        assertTrue(!messages.toString().contains("msg-0"));
        assertTrue(messages.toString().contains("msg-" + (CodeGenerationChatMemoryFactory.MAX_MESSAGES + 4)));
    }
}
