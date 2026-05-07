package org.ymz.app.deployment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.config.AppDevConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 将构建产物发布到静态部署目录。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AppDeploymentFileService {

    private final AppDevConfig appDevConfig;

    public Path deployDist(Path distPath, String deployKey) throws IOException {
        if (deployKey == null || !deployKey.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("部署标识不合法");
        }

        Path deployRoot = Path.of(appDevConfig.getDeploymentRoot()).toAbsolutePath().normalize();
        Files.createDirectories(deployRoot);

        Path targetPath = deployRoot.resolve(deployKey).normalize();
        ensureInside(deployRoot, targetPath);

        Path tempPath = deployRoot.resolve(deployKey + ".tmp-" + UUID.randomUUID()).normalize();
        Path backupPath = deployRoot.resolve(deployKey + ".bak-" + UUID.randomUUID()).normalize();
        ensureInside(deployRoot, tempPath);
        ensureInside(deployRoot, backupPath);

        try {
            copyDirectory(distPath, tempPath);
            replaceDirectory(tempPath, targetPath, backupPath);
            return targetPath;
        } finally {
            deleteIfExists(tempPath);
            deleteIfExists(backupPath);
        }
    }

    private void replaceDirectory(Path tempPath, Path targetPath, Path backupPath) throws IOException {
        if (!Files.exists(targetPath)) {
            Files.move(tempPath, targetPath);
            return;
        }

        Files.move(targetPath, backupPath);
        try {
            Files.move(tempPath, targetPath);
        } catch (IOException e) {
            if (Files.exists(backupPath) && !Files.exists(targetPath)) {
                Files.move(backupPath, targetPath);
            }
            throw e;
        }
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
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
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
            throw new IllegalStateException("路径不在允许的部署目录内：" + path);
        }
    }
}
