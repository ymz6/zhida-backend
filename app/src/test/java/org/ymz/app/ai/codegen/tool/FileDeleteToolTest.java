package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDeleteToolTest {

    @TempDir
    Path workspacePath;

    @Test
    void deleteFileDeletesSingleFile() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Path file = workspacePath.resolve("src/pages/IndexPage.jsx");
        Files.writeString(file, "export default function IndexPage() {}");
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        String result = new FileDeleteTool(session).deleteFile("src/pages/IndexPage.jsx");

        assertTrue(result.contains("已删除"));
        assertFalse(Files.exists(file));
    }
}
