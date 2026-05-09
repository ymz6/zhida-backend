package org.ymz.app.ai.codegen.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGenerationWorkspaceRulesTest {

    @TempDir
    Path workspacePath;

    @Test
    void toRelativeUsesForwardSlash() {
        Path file = workspacePath.resolve("src").resolve("pages").resolve("IndexPage.jsx");

        assertEquals("src/pages/IndexPage.jsx", CodeGenerationWorkspaceRules.toRelative(workspacePath, file));
    }

    @Test
    void detectsIgnoredDirectories() {
        assertTrue(CodeGenerationWorkspaceRules.isIgnoredRelativePath("node_modules/pkg/index.js"));
        assertTrue(CodeGenerationWorkspaceRules.isIgnoredRelativePath("dist/index.html"));
        assertTrue(CodeGenerationWorkspaceRules.isIgnoredRelativePath(".git/config"));
        assertFalse(CodeGenerationWorkspaceRules.isIgnoredRelativePath("src/pages/IndexPage.jsx"));
    }

    @Test
    void detectsProtectedPaths() {
        assertTrue(CodeGenerationWorkspaceRules.isProtectedRelativePath("package.json"));
        assertTrue(CodeGenerationWorkspaceRules.isProtectedRelativePath("src/components/ui/button.jsx"));
        assertFalse(CodeGenerationWorkspaceRules.isProtectedRelativePath("src/components/AppHeader.jsx"));
    }
}
