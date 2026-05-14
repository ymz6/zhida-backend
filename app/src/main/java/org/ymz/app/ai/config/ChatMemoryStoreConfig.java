package org.ymz.app.ai.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.community.store.memory.chat.redis.StoreType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆存储配置
 * 目前使用 Redis 集成
 * 
 * @author ymz
 */
// @Data
@Configuration
@RequiredArgsConstructor
public class ChatMemoryStoreConfig {
    @Bean
    public RedisChatMemoryStore redisChatMemoryStore(RedisProperties redisProperties) {
        return RedisChatMemoryStore.builder()
                .host(redisProperties.getHost())
                .port(redisProperties.getPort())
                .prefix("zhida-app-chat-memory:")
                .storeType(StoreType.STRING)
                .build();
    }
}
