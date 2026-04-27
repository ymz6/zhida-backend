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
}
