package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.ai.codegen.workspace.CodeGenerationProjectVerifier;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckToolTest {

    @TempDir
    Path workspacePath;

    @Test
    void checkMarksLintPassedWhenCommandSucceeds() {
        CodeGenerationProjectVerifier verifier = mock(CodeGenerationProjectVerifier.class);
        when(verifier.runLint(1L, 2L, workspacePath)).thenReturn(result("lint", true));
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, verifier, 1L, 2L);

        String result = new CheckTool(session).check();

        assertTrue(result.contains("pnpm lint 已通过"));
        assertDoesNotThrow(session::requireLintPassed);
        verify(verifier).runLint(1L, 2L, workspacePath);
    }

    @Test
    void checkReportsLintFailure() {
        CodeGenerationProjectVerifier verifier = mock(CodeGenerationProjectVerifier.class);
        when(verifier.runLint(1L, 2L, workspacePath)).thenReturn(result("lint", false));
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, verifier, 1L, 2L);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new CheckTool(session).check());

        assertTrue(error.getMessage().contains("pnpm lint 未通过"));
        verify(verifier).runLint(1L, 2L, workspacePath);
    }

    private CodeGenerationCommandResult result(String command, boolean success) {
        return CodeGenerationCommandResult.builder()
                .commandText("pnpm.cmd " + command)
                .content("$ pnpm.cmd " + command + "\noutput")
                .exitCode(success ? 0 : 1)
                .success(success)
                .build();
    }
}
