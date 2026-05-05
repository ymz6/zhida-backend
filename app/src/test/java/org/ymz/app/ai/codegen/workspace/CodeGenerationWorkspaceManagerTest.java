package org.ymz.app.ai.codegen.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.config.AppGenerationProperties;

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
        AppGenerationProperties properties = new AppGenerationProperties();
        properties.setPreviewRoot(tempDir.resolve("previews").toString());
        properties.setPreviewUrlPrefix("/previews");
        CodeGenerationWorkspaceManager manager = new CodeGenerationWorkspaceManager(properties);

        String previewUrl = manager.refreshPreview(9L, workspacePath);

        assertEquals("/previews/9/index.html", previewUrl);
        assertTrue(Files.isRegularFile(tempDir.resolve("previews/9/index.html")));
        assertTrue(Files.isRegularFile(tempDir.resolve("previews/9/assets/app.js")));
    }
}
