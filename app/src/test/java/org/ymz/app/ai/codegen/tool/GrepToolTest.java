package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrepToolTest {

    @TempDir
    Path workspacePath;

    @Test
    void grepSupportsFixedStringAndRegexSearch() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), """
                const title = 'Alpha';
                const count = 123;
                """);
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        GrepTool grepTool = new GrepTool(session);

        String fixedResult = grepTool.grep("Alpha", ".", false);
        String regexResult = grepTool.grep("count\\s*=\\s*\\d+", ".", true);

        assertTrue(fixedResult.contains("src/pages/IndexPage.jsx:1"));
        assertTrue(regexResult.contains("src/pages/IndexPage.jsx:2"));
    }

    @Test
    void grepRejectsInvalidRegex() {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new GrepTool(session).grep("(", ".", true)
        );

        assertTrue(error.getMessage().contains("正则表达式不合法"));
    }
}
