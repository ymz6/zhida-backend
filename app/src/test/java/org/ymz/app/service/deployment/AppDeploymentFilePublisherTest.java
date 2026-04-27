package org.ymz.app.service.deployment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.config.AppDeploymentProperties;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppDeploymentFilePublisherTest {

    @TempDir
    Path tempDir;

    @Test
    void publishCopiesDistFiles() throws Exception {
        Path distPath = distPath("new");
        AppDeploymentFilePublisher publisher = publisher();

        Path deployPath = publisher.publish(distPath, "abc123");

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
        AppDeploymentFilePublisher publisher = publisher();

        Path deployPath = publisher.publish(distPath, "abc123");

        assertEquals("new", Files.readString(deployPath.resolve("assets/app.js")));
        assertTrue(Files.notExists(deployPath.resolve("assets/old.js")));
    }

    @Test
    void publishRejectsDeployKeyThatEscapesRoot() throws Exception {
        Path distPath = distPath("new");
        AppDeploymentFilePublisher publisher = publisher();

        assertThrows(IllegalStateException.class, () -> publisher.publish(distPath, "../escape"));
    }

    private Path distPath(String content) throws Exception {
        Path distPath = tempDir.resolve("workspace/dist");
        Files.createDirectories(distPath.resolve("assets"));
        Files.writeString(distPath.resolve("index.html"), "<html></html>");
        Files.writeString(distPath.resolve("assets/app.js"), content);
        return distPath;
    }

    private AppDeploymentFilePublisher publisher() {
        AppDeploymentProperties properties = new AppDeploymentProperties();
        properties.setDeployRoot(tempDir.resolve("deployments").toString());
        return new AppDeploymentFilePublisher(properties);
    }
}
