package org.ymz.app.ai.codegen.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeGenerationProjectVerifierTest {

    @TempDir
    Path workspacePath;

    @Test
    void returnsNullWhenLintAndPreviewBuildPass() {
        CodeGenerationCommandRunner commandRunner = mock(CodeGenerationCommandRunner.class);
        CodeGenerationProjectVerifier verifier = new CodeGenerationProjectVerifier(commandRunner);
        when(commandRunner.runPnpmCommandResult(1L, 2L, workspacePath, CodeGenerationCommandRunner.LogMode.SUMMARY, "lint"))
                .thenReturn(result("lint", true));
        when(commandRunner.runPnpmCommandResult(1L, 2L, workspacePath, CodeGenerationCommandRunner.LogMode.SUMMARY, "build:preview"))
                .thenReturn(result("build:preview", true));

        assertNull(verifier.verify(1L, 2L, workspacePath));

        verify(commandRunner).runPnpmCommandResult(1L, 2L, workspacePath, CodeGenerationCommandRunner.LogMode.SUMMARY, "lint");
        verify(commandRunner).runPnpmCommandResult(1L, 2L, workspacePath, CodeGenerationCommandRunner.LogMode.SUMMARY, "build:preview");
    }

    @Test
    void stopsAtFailedLint() {
        CodeGenerationCommandRunner commandRunner = mock(CodeGenerationCommandRunner.class);
        CodeGenerationProjectVerifier verifier = new CodeGenerationProjectVerifier(commandRunner);
        CodeGenerationCommandResult lintFailed = result("lint", false);
        when(commandRunner.runPnpmCommandResult(1L, 2L, workspacePath, CodeGenerationCommandRunner.LogMode.SUMMARY, "lint"))
                .thenReturn(lintFailed);

        assertEquals(lintFailed, verifier.verify(1L, 2L, workspacePath));

        verify(commandRunner, never()).runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(CodeGenerationCommandRunner.LogMode.SUMMARY),
                eq("build:preview")
        );
    }

    private CodeGenerationCommandResult result(String command, boolean success) {
        return CodeGenerationCommandResult.builder()
                .commandText("pnpm.cmd " + command)
                .content("$ pnpm.cmd " + command)
                .exitCode(success ? 0 : 1)
                .success(success)
                .build();
    }
}
