package org.ymz.app.oss;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OSS 配置
 *
 * @author ymz
 */
@Configuration
public class OssConfig {

    @Bean
    public OssClient ossClient(OssClient.Properties properties) {
        return new OssClient(properties);
    }
}
