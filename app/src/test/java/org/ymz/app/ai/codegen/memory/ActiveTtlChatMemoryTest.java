package org.ymz.app.ai.codegen.memory;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveTtlChatMemoryTest {

    @Test
    void clearsExpiredMessagesOnNextAccess() throws Exception {
        ChatMemory delegate = MessageWindowChatMemory.builder()
                .id("app:1")
                .maxMessages(10)
                .chatMemoryStore(new InMemoryChatMemoryStore())
                .build();
        ActiveTtlChatMemory memory = new ActiveTtlChatMemory(delegate, Duration.ofMillis(10));

        memory.add(UserMessage.from("first"));
        Thread.sleep(20);

        assertEquals(0, memory.messages().size());
    }
}
