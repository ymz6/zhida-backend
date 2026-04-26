package org.ymz.app.service.generation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.config.AppGenerationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 处理项目模板复制和静态预览发布。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class ProjectWorkspaceManager {

    private final AppGenerationProperties properties;

    public Path initializeWorkspace(Long appId) throws IOException {
        Path templatePath = Path.of(properties.getTemplatePath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(templatePath)) {
            throw new IllegalStateException("项目模板不存在：" + templatePath);
        }

        Path workspaceRoot = Path.of(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
        Files.createDirectories(workspaceRoot);
        Path workspacePath = workspaceRoot.resolve(String.valueOf(appId)).normalize();
        ensureInside(workspaceRoot, workspacePath);
        deleteIfExists(workspacePath);
        copyDirectory(templatePath, workspacePath);
        return workspacePath;
    }

    public String publishPreview(Long appId, Path workspacePath) throws IOException {
        Path distPath = workspacePath.resolve("dist").normalize();
        if (!Files.isDirectory(distPath) || !Files.isRegularFile(distPath.resolve("index.html"))) {
            throw new IllegalStateException("构建产物不存在，请检查 dist/index.html");
        }

        Path previewRoot = Path.of(properties.getPreviewRoot()).toAbsolutePath().normalize();
        Files.createDirectories(previewRoot);
        Path previewPath = previewRoot.resolve(String.valueOf(appId)).normalize();
        ensureInside(previewRoot, previewPath);
        deleteIfExists(previewPath);
        copyDirectory(distPath, previewPath);

        String prefix = properties.getPreviewUrlPrefix();
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (prefix.endsWith("/")) {
            return prefix + appId + "/index.html";
        }
        return prefix + "/" + appId + "/index.html";
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path sourcePath : stream.toList()) {
                Path relativePath = source.relativize(sourcePath);
                Path targetPath = target.resolve(relativePath).normalize();
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath);
                }
            }
        }
    }

    private void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(item);
            }
        }
    }

    private void ensureInside(Path root, Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalStateException("路径不在允许的工作目录内：" + path);
        }
    }
}
