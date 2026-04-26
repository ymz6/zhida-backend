package org.ymz.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用生成相关路径配置。
 *
 * @author ymz
 */
@Data
@ConfigurationProperties(prefix = "app.generation")
public class AppGenerationProperties {

    private String templatePath = "project-template/zhida-react-project";

    private String workspaceRoot = "tmp/app-workspaces";

    private String previewRoot = "tmp/app-previews";

    private String previewUrlPrefix = "/previews";
}
