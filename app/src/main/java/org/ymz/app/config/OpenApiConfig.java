package org.ymz.app.config;

import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 全局配置
 * @author ymz
 */
@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig()
                .replaceWithSchema(Long.class, new StringSchema().format("int64"))
                .replaceWithSchema(Long.TYPE, new StringSchema().format("int64"));
    }
}
