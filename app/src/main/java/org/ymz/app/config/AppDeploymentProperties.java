package org.ymz.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用部署相关路径配置。
 *
 * @author ymz
 */
@Data
@ConfigurationProperties(prefix = "app.deployment")
public class AppDeploymentProperties {

    private String deployRoot = "nginx/html/apps";

    private String deployUrlPrefix = "http://localhost/apps";

    private Cover cover = new Cover();

    @Data
    public static class Cover {

        private boolean enabled = true;

        private int viewportWidth = 1440;

        private int viewportHeight = 900;

        private int pageLoadTimeoutSeconds = 30;

        private long settleDelayMillis = 2000;

        private float quality = 0.8f;

        private String ossPrefix = "app-covers/";
    }
}
