package org.ymz.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 应用生成长任务虚拟线程执行器配置。
 *
 * @author ymz
 */
@Configuration
public class AppTaskExecutorConfig {

    @Bean("appTaskExecutor")
    public Executor appTaskExecutor() {
        return new VirtualThreadTaskExecutor("app-task-");
    }
}
