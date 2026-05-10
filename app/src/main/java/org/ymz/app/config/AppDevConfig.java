package org.ymz.app.config;

import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * 开发阶段内置的应用业务配置。
 *
 * @author ymz
 */
@Getter
@Component
public class AppDevConfig {

    /**
     * 应用生成时使用的前端项目模板目录。
     */
    private final String generationTemplatePath;

    /**
     * 应用生成工作区根目录。
     */
    private final String generationWorkspaceRoot;

    /**
     * 预览静态文件根目录。
     */
    private final String generationPreviewRoot;

    /**
     * 预览页面访问 URL 前缀。
     */
    private final String generationPreviewUrlPrefix;

    /**
     * 应用正式部署目录。
     */
    private final String deploymentRoot;

    /**
     * 应用部署后的访问 URL 前缀。
     */
    private final String deploymentUrlPrefix;

    /**
     * 封面截图浏览器视口宽度。
     */
    private final int coverViewportWidth;

    /**
     * 封面截图浏览器视口高度。
     */
    private final int coverViewportHeight;

    /**
     * 封面截图页面加载超时时间，单位秒。
     */
    private final int coverPageLoadTimeoutSeconds;

    /**
     * 封面截图前额外等待渲染完成的时间，单位毫秒。
     */
    private final long coverSettleDelayMillis;

    /**
     * 封面 JPEG 压缩质量。
     */
    private final float coverQuality;

    /**
     * 封面图上传到 OSS 时使用的对象前缀。
     */
    private final String coverOssPrefix;

    public AppDevConfig() {
        this(
                "project-template/zhida-react-project",
                "tmp/app-workspaces",
                "tmp/app-previews",
                "/previews",
                "nginx/html/apps",
                "http://localhost/apps",
                1440,
                900,
                30,
                2000,
                0.8f,
                "app-covers/"
        );
    }

    public AppDevConfig(
            String generationTemplatePath,
            String generationWorkspaceRoot,
            String generationPreviewRoot,
            String generationPreviewUrlPrefix,
            String deploymentRoot,
            String deploymentUrlPrefix,
            int coverViewportWidth,
            int coverViewportHeight,
            int coverPageLoadTimeoutSeconds,
            long coverSettleDelayMillis,
            float coverQuality,
            String coverOssPrefix
    ) {
        this.generationTemplatePath = generationTemplatePath;
        this.generationWorkspaceRoot = generationWorkspaceRoot;
        this.generationPreviewRoot = generationPreviewRoot;
        this.generationPreviewUrlPrefix = generationPreviewUrlPrefix;
        this.deploymentRoot = deploymentRoot;
        this.deploymentUrlPrefix = deploymentUrlPrefix;
        this.coverViewportWidth = coverViewportWidth;
        this.coverViewportHeight = coverViewportHeight;
        this.coverPageLoadTimeoutSeconds = coverPageLoadTimeoutSeconds;
        this.coverSettleDelayMillis = coverSettleDelayMillis;
        this.coverQuality = coverQuality;
        this.coverOssPrefix = coverOssPrefix;
    }

    public AppDevConfig(
            String generationTemplatePath,
            String generationWorkspaceRoot,
            String generationPreviewRoot,
            String generationPreviewUrlPrefix,
            String deploymentRoot,
            String deploymentUrlPrefix,
            int coverViewportWidth,
            int coverViewportHeight,
            int coverPageLoadTimeoutSeconds,
            long coverSettleDelayMillis,
            float coverQuality,
            String coverOssPrefix,
            String ignoredPrometheusBaseUrl
    ) {
        this(
                generationTemplatePath,
                generationWorkspaceRoot,
                generationPreviewRoot,
                generationPreviewUrlPrefix,
                deploymentRoot,
                deploymentUrlPrefix,
                coverViewportWidth,
                coverViewportHeight,
                coverPageLoadTimeoutSeconds,
                coverSettleDelayMillis,
                coverQuality,
                coverOssPrefix
        );
    }
}
