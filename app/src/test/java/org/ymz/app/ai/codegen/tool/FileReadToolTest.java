package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileReadToolTest {

    @TempDir
    Path workspacePath;

    @Test
    void readFileReturnsContentAndMarksFileAsRead() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "const title = 'Alpha';");
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        String content = new FileReadTool(session).readFile("src/pages/IndexPage.jsx");

        assertTrue(content.contains("Alpha"));
        session.requireRead(workspacePath.resolve("src/pages/IndexPage.jsx"));
    }
}
