package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileEditToolTest {

    @TempDir
    Path workspacePath;

    @Test
    void editFileRequiresReadAndReplacesUniqueText() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), """
                const title = 'Alpha';
                const enabled = true;
                """);
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        FileEditTool editTool = new FileEditTool(session);

        IllegalArgumentException unread = assertThrows(
                IllegalArgumentException.class,
                () -> editTool.editFile("src/pages/IndexPage.jsx", "Alpha", "Beta")
        );
        assertTrue(unread.getMessage().contains("请先调用 readFile"));

        new FileReadTool(session).readFile("src/pages/IndexPage.jsx");
        String result = editTool.editFile("src/pages/IndexPage.jsx", "Alpha", "Beta");

        assertTrue(result.contains("已替换"));
        assertTrue(Files.readString(workspacePath.resolve("src/pages/IndexPage.jsx")).contains("Beta"));
    }

    @Test
    void editFileRejectsMissingOrNonUniqueText() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "Alpha Alpha");
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        new FileReadTool(session).readFile("src/pages/IndexPage.jsx");
        FileEditTool editTool = new FileEditTool(session);

        IllegalArgumentException nonUnique = assertThrows(
                IllegalArgumentException.class,
                () -> editTool.editFile("src/pages/IndexPage.jsx", "Alpha", "Beta")
        );
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> editTool.editFile("src/pages/IndexPage.jsx", "Gamma", "Beta")
        );

        assertTrue(nonUnique.getMessage().contains("不唯一"));
        assertTrue(missing.getMessage().contains("未找到"));
    }
}
