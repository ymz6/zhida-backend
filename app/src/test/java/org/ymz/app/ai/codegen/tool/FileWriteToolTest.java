package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileWriteToolTest {

    @TempDir
    Path workspacePath;

    @Test
    void writeFileCreatesNewFileWithoutRead() throws Exception {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        String result = new FileWriteTool(session).writeFile("src/pages/IndexPage.jsx", "const title = 'Alpha';");

        assertTrue(result.contains("已写入"));
        assertEquals("const title = 'Alpha';", Files.readString(workspacePath.resolve("src/pages/IndexPage.jsx")));
    }

    @Test
    void writeFileRequiresReadBeforeOverwritingExistingFile() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "old");
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        FileWriteTool writeTool = new FileWriteTool(session);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> writeTool.writeFile("src/pages/IndexPage.jsx", "new")
        );
        assertTrue(error.getMessage().contains("请先调用 readFile"));

        new FileReadTool(session).readFile("src/pages/IndexPage.jsx");
        writeTool.writeFile("src/pages/IndexPage.jsx", "new");

        assertEquals("new", Files.readString(workspacePath.resolve("src/pages/IndexPage.jsx")));
    }
}
