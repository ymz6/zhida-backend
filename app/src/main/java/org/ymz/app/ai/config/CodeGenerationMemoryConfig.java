package org.ymz.app.ai.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.community.store.memory.chat.redis.StoreType;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CodeGenerationAgent 对话记忆配置。
 *
 * @author ymz
 */
@Configuration
public class CodeGenerationMemoryConfig {

    private static final String REDIS_KEY_PREFIX = "zhida:codegen:memory:";

    @Bean
    public ChatMemoryStore codeGenerationChatMemoryStore(RedisProperties redisProperties) {
        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(redisProperties.getHost())
                .port(redisProperties.getPort())
                .prefix(REDIS_KEY_PREFIX)
                .ttl(0L)
                .storeType(StoreType.STRING);

        String password = redisProperties.getPassword();
        if (StrUtil.isNotBlank(password)) {
            builder.user(StrUtil.blankToDefault(redisProperties.getUsername(), "default"))
                    .password(password);
        }

        return builder.build();
    }
}
