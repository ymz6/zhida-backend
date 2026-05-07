package org.ymz.app.ai.codegen.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.config.AppDevConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGenerationWorkspaceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void refreshPreviewCopiesDistAndReturnsIndexHtmlUrl() throws Exception {
        Path workspacePath = tempDir.resolve("workspace");
        Files.createDirectories(workspacePath.resolve("dist/assets"));
        Files.writeString(workspacePath.resolve("dist/index.html"), "<html></html>");
        Files.writeString(workspacePath.resolve("dist/assets/app.js"), "console.log('ok')");
        AppDevConfig appDevConfig = new AppDevConfig(
                "project-template/zhida-react-project",
                "tmp/app-workspaces",
                tempDir.resolve("previews").toString(),
                "/previews",
                "nginx/html/apps",
                "http://localhost/apps",
                1440,
                900,
                30,
                2000,
                0.8f,
                "app-covers/",
                "http://localhost:9090"
        );
        CodeGenerationWorkspaceManager manager = new CodeGenerationWorkspaceManager(appDevConfig);

        String previewUrl = manager.refreshPreview(9L, workspacePath);

        assertEquals("/previews/9/index.html", previewUrl);
        assertTrue(Files.isRegularFile(tempDir.resolve("previews/9/index.html")));
        assertTrue(Files.isRegularFile(tempDir.resolve("previews/9/assets/app.js")));
    }
}
