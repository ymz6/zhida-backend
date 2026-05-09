package org.ymz.app.ai.codegen.tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.ai.codegen.workspace.CodeGenerationProjectVerifier;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceToolSessionTest {

    @TempDir
    Path workspacePath;

    @Test
    void writeReadAndSearchRespectWorkspaceRules() throws Exception {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        String writeResult = session.writeFile("src/pages/IndexPage.jsx", "const title = 'Alpha';");
        String readResult = session.readFile("src/pages/IndexPage.jsx");
        String searchResult = session.searchFiles("Alpha", ".");

        assertTrue(writeResult.contains("已写入"));
        assertTrue(readResult.contains("Alpha"));
        assertTrue(searchResult.contains("src/pages/IndexPage.jsx:1"));
    }

    @Test
    void writeRejectsProtectedTemplateFile() {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> session.writeFile("src/components/ui/button.jsx", "bad")
        );

        assertTrue(error.getMessage().contains("不允许修改"));
    }

    @Test
    void replaceInFileReplacesUniqueText() throws Exception {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), """
                const title = 'Alpha';
                const enabled = true;
                """);

        String result = session.replaceInFile(
                "src/pages/IndexPage.jsx",
                "const title = 'Alpha';\n",
                ""
        );

        assertTrue(result.contains("已替换"));
        assertEquals("const enabled = true;\n", Files.readString(workspacePath.resolve("src/pages/IndexPage.jsx")));
    }

    @Test
    void replaceInFileRequiresCheckProjectAgain() throws Exception {
        CodeGenerationProjectVerifier projectVerifier = mock(CodeGenerationProjectVerifier.class);
        when(projectVerifier.verify(1L, 2L, workspacePath)).thenReturn(null);
        WorkspaceToolSession session = new WorkspaceToolSession(
                workspacePath,
                projectVerifier,
                1L,
                2L
        );
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "const title = 'Alpha';");

        session.checkProject();
        session.replaceInFile("src/pages/IndexPage.jsx", "Alpha", "Beta");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> session.finish("done"));
        assertTrue(error.getMessage().contains("尚未通过 checkProject"));
    }

    @Test
    void replaceInFileRejectsMissingText() throws Exception {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "const title = 'Alpha';");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> session.replaceInFile("src/pages/IndexPage.jsx", "Beta", "Gamma")
        );

        assertTrue(error.getMessage().contains("未找到"));
    }

    @Test
    void replaceInFileRejectsNonUniqueText() throws Exception {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "Alpha Alpha");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> session.replaceInFile("src/pages/IndexPage.jsx", "Alpha", "Beta")
        );

        assertTrue(error.getMessage().contains("不唯一"));
    }

    @Test
    void replaceInFileRejectsProtectedTemplateFile() throws Exception {
        WorkspaceToolSession session = new WorkspaceToolSession(workspacePath, null, null, null);
        Files.createDirectories(workspacePath.resolve("src/components/ui"));
        Files.writeString(workspacePath.resolve("src/components/ui/button.jsx"), "export const Button = null;");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> session.replaceInFile("src/components/ui/button.jsx", "null", "undefined")
        );

        assertTrue(error.getMessage().contains("不允许修改"));
    }

    @Test
    void finishRequiresSuccessfulCheckProject() throws Exception {
        CodeGenerationProjectVerifier projectVerifier = mock(CodeGenerationProjectVerifier.class);
        when(projectVerifier.verify(1L, 2L, workspacePath)).thenReturn(null);
        WorkspaceToolSession session = new WorkspaceToolSession(
                workspacePath,
                projectVerifier,
                1L,
                2L
        );

        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "export default function IndexPage() {}");

        assertThrows(IllegalStateException.class, () -> session.finish("done"));
        String checkResult = session.checkProject();
        String finishResult = session.finish("done");

        assertTrue(checkResult.contains("已通过"));
        assertFalse(finishResult.isBlank());
        verify(projectVerifier).verify(1L, 2L, workspacePath);
    }

    @Test
    void failedLintPreventsBuild() {
        CodeGenerationProjectVerifier projectVerifier = mock(CodeGenerationProjectVerifier.class);
        when(projectVerifier.verify(1L, 2L, workspacePath)).thenReturn(failed("lint"));
        WorkspaceToolSession session = new WorkspaceToolSession(
                workspacePath,
                projectVerifier,
                1L,
                2L
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, session::checkProject);

        assertTrue(error.getMessage().contains("pnpm lint 未通过"));
        verify(projectVerifier).verify(1L, 2L, workspacePath);
    }

    @Test
    void failedPreviewBuildReportsPreviewCommand() {
        CodeGenerationProjectVerifier projectVerifier = mock(CodeGenerationProjectVerifier.class);
        when(projectVerifier.verify(1L, 2L, workspacePath)).thenReturn(failed("build:preview"));
        WorkspaceToolSession session = new WorkspaceToolSession(
                workspacePath,
                projectVerifier,
                1L,
                2L
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, session::checkProject);

        assertTrue(error.getMessage().contains("pnpm build:preview 未通过"));
        verify(projectVerifier).verify(1L, 2L, workspacePath);
    }

    private CodeGenerationCommandResult failed(String command) {
        return CodeGenerationCommandResult.builder()
                .commandText("pnpm.cmd " + command)
                .content("$ pnpm.cmd " + command + "\nerror")
                .exitCode(1)
                .success(false)
                .build();
    }
}
