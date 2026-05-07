package org.ymz.app.deployment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.config.AppDevConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppDeploymentFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void publishCopiesDistFiles() throws Exception {
        Path distPath = distPath("new");
        AppDeploymentFileService publisher = publisher();

        Path deployPath = publisher.deployDist(distPath, "abc123");

        assertEquals(tempDir.resolve("deployments/abc123").toAbsolutePath().normalize(), deployPath);
        assertTrue(Files.isRegularFile(deployPath.resolve("index.html")));
        assertTrue(Files.isRegularFile(deployPath.resolve("assets/app.js")));
        assertEquals("new", Files.readString(deployPath.resolve("assets/app.js")));
    }

    @Test
    void publishReplacesOldDeployment() throws Exception {
        Path oldPath = tempDir.resolve("deployments/abc123");
        Files.createDirectories(oldPath.resolve("assets"));
        Files.writeString(oldPath.resolve("index.html"), "old");
        Files.writeString(oldPath.resolve("assets/old.js"), "old");
        Path distPath = distPath("new");
        AppDeploymentFileService publisher = publisher();

        Path deployPath = publisher.deployDist(distPath, "abc123");

        assertEquals("new", Files.readString(deployPath.resolve("assets/app.js")));
        assertTrue(Files.notExists(deployPath.resolve("assets/old.js")));
    }

    @Test
    void publishRejectsDeployKeyThatEscapesRoot() throws Exception {
        Path distPath = distPath("new");
        AppDeploymentFileService publisher = publisher();

        assertThrows(IllegalStateException.class, () -> publisher.deployDist(distPath, "../escape"));
    }

    private Path distPath(String content) throws Exception {
        Path distPath = tempDir.resolve("workspace/dist");
        Files.createDirectories(distPath.resolve("assets"));
        Files.writeString(distPath.resolve("index.html"), "<html></html>");
        Files.writeString(distPath.resolve("assets/app.js"), content);
        return distPath;
    }

    private AppDeploymentFileService publisher() {
        AppDevConfig appDevConfig = new AppDevConfig(
                "project-template/zhida-react-project",
                "tmp/app-workspaces",
                "tmp/app-previews",
                "/previews",
                tempDir.resolve("deployments").toString(),
                "http://localhost/apps",
                1440,
                900,
                30,
                2000,
                0.8f,
                "app-covers/",
                "http://localhost:9090"
        );
        return new AppDeploymentFileService(appDevConfig);
    }
}
