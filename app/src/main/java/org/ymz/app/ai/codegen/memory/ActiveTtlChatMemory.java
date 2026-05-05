package org.ymz.app.ai.codegen.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带活跃 TTL 的短期工作记忆包装。
 *
 * @author ymz
 */
public class ActiveTtlChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final long ttlMillis;
    private final AtomicLong lastTouchedAt = new AtomicLong(System.currentTimeMillis());

    public ActiveTtlChatMemory(ChatMemory delegate, Duration ttl) {
        this.delegate = delegate;
        this.ttlMillis = ttl.toMillis();
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        clearIfExpired();
        delegate.add(message);
        touch();
    }

    @Override
    public List<ChatMessage> messages() {
        clearIfExpired();
        List<ChatMessage> messages = delegate.messages();
        touch();
        return messages;
    }

    @Override
    public void clear() {
        delegate.clear();
        touch();
    }

    private void clearIfExpired() {
        if (ttlMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastTouchedAt.get();
        if (now - last > ttlMillis) {
            // 即使 AiServices 进程内复用了同一个 ChatMemory 实例，超过活跃期后也会在下一次访问时清空旧内容。
            delegate.clear();
        }
    }

    private void touch() {
        lastTouchedAt.set(System.currentTimeMillis());
    }
}
