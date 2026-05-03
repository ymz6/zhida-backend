package org.ymz.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 运行与用量监控配置。
 *
 * @author ymz
 */
@Data
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringProperties {

    private Prometheus prometheus = new Prometheus();

    @Data
    public static class Prometheus {

        private String baseUrl = "http://localhost:9090";
    }
}
