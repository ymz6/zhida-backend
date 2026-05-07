package org.ymz.app.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.ymz.app.security.AuthInterceptor;

import java.nio.file.Path;

/**
 * MVC 鉴权配置
 *
 * @author ymz
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcAuthConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final AppDevConfig appDevConfig;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String previewPattern = appDevConfig.getGenerationPreviewUrlPrefix();
        if (!previewPattern.startsWith("/")) {
            previewPattern = "/" + previewPattern;
        }
        if (!previewPattern.endsWith("/")) {
            previewPattern = previewPattern + "/";
        }
        String previewLocation = Path.of(appDevConfig.getGenerationPreviewRoot())
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        if (!previewLocation.endsWith("/")) {
            previewLocation = previewLocation + "/";
        }
        registry.addResourceHandler(previewPattern + "**")
                .addResourceLocations(previewLocation);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        String previewPattern = appDevConfig.getGenerationPreviewUrlPrefix();
        if (!previewPattern.startsWith("/")) {
            previewPattern = "/" + previewPattern;
        }
        if (!previewPattern.endsWith("/")) {
            previewPattern = previewPattern + "/";
        }
        registry.addRedirectViewController(previewPattern + "{appId}", previewPattern + "{appId}/index.html");
        registry.addRedirectViewController(previewPattern + "{appId}/", previewPattern + "{appId}/index.html");
    }
}
