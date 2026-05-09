package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.ai.codegen.workspace.CodeGenerationProjectVerifier;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildToolTest {

    @TempDir
    Path workspacePath;

    @Test
    void buildRequiresPassedCheck() {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new BuildTool(session).build());

        assertTrue(error.getMessage().contains("请先调用 check"));
    }

    @Test
    void buildMarksBuildPassedWhenCommandSucceeds() {
        CodeGenerationProjectVerifier verifier = mock(CodeGenerationProjectVerifier.class);
        when(verifier.runBuild(1L, 2L, workspacePath)).thenReturn(result("build:preview", true));
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, verifier, 1L, 2L);
        session.markLintPassed();

        String result = new BuildTool(session).build();

        assertTrue(result.contains("pnpm build:preview 已通过"));
        verify(verifier).runBuild(1L, 2L, workspacePath);
    }

    @Test
    void buildReportsPreviewBuildFailure() {
        CodeGenerationProjectVerifier verifier = mock(CodeGenerationProjectVerifier.class);
        when(verifier.runBuild(1L, 2L, workspacePath)).thenReturn(result("build:preview", false));
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, verifier, 1L, 2L);
        session.markLintPassed();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new BuildTool(session).build());

        assertTrue(error.getMessage().contains("pnpm build:preview 未通过"));
        verify(verifier).runBuild(1L, 2L, workspacePath);
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
