package org.ymz.app.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/**
 * 应用生成相关的本地路径配置。
 *
 * @author ymz
 */
@Getter
@Validated
@ConfigurationProperties(prefix = "zhida.app-path")
public class AppPathProperties {

    @NotNull
    private final Path tmpDir;

    @NotNull
    private final Path templateDir;

    public AppPathProperties(Path tmpDir, Path templateDir) {
        if (tmpDir == null || templateDir == null) {
            throw new IllegalArgumentException("zhida.app-path 路径不能为空");
        }
        if (!tmpDir.isAbsolute() || !templateDir.isAbsolute()) {
            throw new IllegalArgumentException("zhida.app-path 路径必须是绝对路径");
        }
        this.tmpDir = tmpDir.normalize();
        this.templateDir = templateDir.normalize();
    }
}
