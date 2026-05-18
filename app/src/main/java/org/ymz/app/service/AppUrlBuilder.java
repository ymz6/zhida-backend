package org.ymz.app.service;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 生成应用预览和部署访问地址。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AppUrlBuilder {

    private final Properties properties;

    public String buildPreviewUrl(Long appId) {
        return properties.backendBaseUrl() + "/apps/preview/" + appId + "/";
    }

    public String buildDeployUrl(String deployKey) {
        if (StrUtil.isBlank(deployKey)) {
            return null;
        }
        return properties.deployBaseUrl() + "/" + deployKey + "/";
    }

    /**
     * 应用访问地址配置，仅服务于 {@link AppUrlBuilder}。
     */
    @ConfigurationProperties(prefix = "zhida.app-url")
    public record Properties(
            String backendBaseUrl,
            String deployBaseUrl) {
    }
}
