package org.ymz.app.ai.codegen.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceToolSessionTest {

    @TempDir
    Path workspacePath;

    @Test
    void rejectsPathTraversalAndProtectedPaths() {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        IllegalArgumentException traversal = assertThrows(
                IllegalArgumentException.class,
                () -> session.resolveReadPath("../outside.jsx")
        );
        IllegalArgumentException protectedPath = assertThrows(
                IllegalArgumentException.class,
                () -> session.resolveWritePath("src/components/ui/button.jsx")
        );

        assertTrue(traversal.getMessage().contains("路径越界"));
        assertTrue(protectedPath.getMessage().contains("不允许修改"));
    }

    @Test
    void readGuardRequiresLatestReadBeforeModification() {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        Path file = workspacePath.resolve("src/pages/IndexPage.jsx");

        assertThrows(IllegalArgumentException.class, () -> session.requireRead(file));

        session.markRead(file);
        session.requireRead(file);
        session.markWritten(file);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> session.requireRead(file));
        assertTrue(error.getMessage().contains("请先调用 readFile"));
    }

    @Test
    void finishRequiresLintAndBuildAfterLastWrite() {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        Path file = workspacePath.resolve("src/pages/IndexPage.jsx");

        assertThrows(IllegalStateException.class, session::requireFinishReady);
        session.markLintPassed();
        assertThrows(IllegalStateException.class, session::requireFinishReady);
        session.markBuildPassed();
        session.requireFinishReady();
        assertEquals("done", session.setFinalSummary("done"));

        session.markWritten(file);

        assertThrows(IllegalStateException.class, session::requireFinishReady);
        assertEquals(null, session.getFinalSummary());
    }
}
