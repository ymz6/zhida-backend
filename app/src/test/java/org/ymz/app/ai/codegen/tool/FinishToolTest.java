package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinishToolTest {

    @TempDir
    Path workspacePath;

    @Test
    void finishRequiresCheckAndBuild() {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new FinishTool(session).finish("done")
        );

        assertTrue(error.getMessage().contains("check 和 build 都已通过"));
    }

    @Test
    void finishStoresSummaryWhenReady() {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        session.markLintPassed();
        session.markBuildPassed();

        String result = new FinishTool(session).finish("done");

        assertEquals("done", result);
        assertEquals("done", session.getFinalSummary());
    }
}
