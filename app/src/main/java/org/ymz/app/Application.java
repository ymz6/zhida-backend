package org.ymz.app;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication(
        // 本项目之用的上 LangChain4j Redis 中的 Chat Memory 板块，需要排除 Embedding 存储后项目才能正常启动
        exclude = {RedisEmbeddingStoreAutoConfiguration.class}
)
@ConfigurationPropertiesScan
@EnableConfigurationProperties
@MapperScan("org.ymz.app.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
