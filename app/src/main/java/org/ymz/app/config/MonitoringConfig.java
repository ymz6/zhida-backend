package org.ymz.app.config;

import dev.langchain4j.micrometer.metrics.listeners.MicrometerMetricsChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 监控配置。
 *
 * @author ymz
 */
@Configuration
public class MonitoringConfig {

    @Bean
    public ChatModelListener micrometerMetricsChatModelListener(MeterRegistry meterRegistry) {
        return new MicrometerMetricsChatModelListener(meterRegistry);
    }
}
