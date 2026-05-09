package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobToolTest {

    @TempDir
    Path workspacePath;

    @Test
    void globMatchesPatternAndIgnoresGeneratedDirectories() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.createDirectories(workspacePath.resolve("node_modules/pkg"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "page");
        Files.writeString(workspacePath.resolve("src/pages/helper.js"), "helper");
        Files.writeString(workspacePath.resolve("node_modules/pkg/Hidden.jsx"), "hidden");
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        String result = new GlobTool(session).glob("**/*.jsx");

        assertTrue(result.contains("src/pages/IndexPage.jsx"));
        assertFalse(result.contains("helper.js"));
        assertFalse(result.contains("node_modules"));
    }
}
