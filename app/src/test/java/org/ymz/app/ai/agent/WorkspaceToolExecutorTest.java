package org.ymz.app.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.service.generation.ProjectCommandResult;
import org.ymz.app.service.generation.ProjectCommandRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceToolExecutorTest {

    @TempDir
    Path workspacePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writeFileAllowsBusinessFiles() throws Exception {
        WorkspaceToolExecutor executor = new WorkspaceToolExecutor(workspacePath, objectMapper);

        WorkspaceToolResult result = executor.execute(ToolExecutionRequest.builder()
                .name("writeFile")
                .arguments("{\"path\":\"src/pages/IndexPage.jsx\",\"content\":\"function IndexPage() { return <div /> }\"}")
                .build());

        assertFalse(result.isError());
        assertTrue(Files.exists(workspacePath.resolve("src/pages/IndexPage.jsx")));
    }

    @Test
    void writeFileRejectsProtectedTemplateFiles() {
        WorkspaceToolExecutor executor = new WorkspaceToolExecutor(workspacePath, objectMapper);

        WorkspaceToolResult result = executor.execute(ToolExecutionRequest.builder()
                .name("writeFile")
                .arguments("{\"path\":\"src/components/ui/button.jsx\",\"content\":\"bad\"}")
                .build());

        assertTrue(result.isError());
    }

    @Test
    void readFileRejectsWorkspaceEscape() {
        WorkspaceToolExecutor executor = new WorkspaceToolExecutor(workspacePath, objectMapper);

        WorkspaceToolResult result = executor.execute(ToolExecutionRequest.builder()
                .name("readFile")
                .arguments("{\"path\":\"../outside.txt\"}")
                .build());

        assertTrue(result.isError());
    }

    @Test
    void searchFilesFindsMatchesAndIgnoresGeneratedDirectories() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.createDirectories(workspacePath.resolve("node_modules/demo"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "const title = 'Alpha';");
        Files.writeString(workspacePath.resolve("node_modules/demo/index.js"), "const title = 'Alpha';");
        WorkspaceToolExecutor executor = new WorkspaceToolExecutor(workspacePath, objectMapper);

        WorkspaceToolResult result = executor.execute(ToolExecutionRequest.builder()
                .name("searchFiles")
                .arguments("{\"query\":\"Alpha\"}")
                .build());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("src/pages/IndexPage.jsx:1"));
        assertFalse(result.getContent().contains("node_modules"));
    }

    @Test
    void searchFilesTreatsQueryAsLiteralText() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "const pattern = 'literal.*value';");
        WorkspaceToolExecutor executor = new WorkspaceToolExecutor(workspacePath, objectMapper);

        WorkspaceToolResult result = executor.execute(ToolExecutionRequest.builder()
                .name("searchFiles")
                .arguments("{\"query\":\"literal.*value\"}")
                .build());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("src/pages/IndexPage.jsx:1"));
    }

    @Test
    void finishRequiresSuccessfulCheckProjectAfterWrite() {
        WorkspaceToolExecutor executor = new WorkspaceToolExecutor(workspacePath, objectMapper);

        WorkspaceToolResult writeResult = executor.execute(ToolExecutionRequest.builder()
                .name("writeFile")
                .arguments("{\"path\":\"src/pages/IndexPage.jsx\",\"content\":\"export default function IndexPage() { return <div /> }\"}")
                .build());
        WorkspaceToolResult finishResult = executor.execute(ToolExecutionRequest.builder()
                .name("finish")
                .arguments("{\"summary\":\"done\"}")
                .build());

        assertFalse(writeResult.isError());
        assertTrue(finishResult.isError());
    }

    @Test
    void checkProjectRunsFixedLintAndBuildThenAllowsFinish() {
        ProjectCommandRunner projectCommandRunner = mock(ProjectCommandRunner.class);
        when(projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                eq("lint")
        )).thenReturn(passed("lint"));
        when(projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                eq("build")
        )).thenReturn(passed("build"));
        WorkspaceToolExecutor executor = new WorkspaceToolExecutor(
                workspacePath,
                objectMapper,
                projectCommandRunner,
                1L,
                2L
        );

        WorkspaceToolResult checkResult = executor.execute(ToolExecutionRequest.builder()
                .name("checkProject")
                .arguments("{\"command\":\"rm -rf /\"}")
                .build());
        WorkspaceToolResult finishResult = executor.execute(ToolExecutionRequest.builder()
                .name("finish")
                .arguments("{\"summary\":\"done\"}")
                .build());

        assertFalse(checkResult.isError());
        assertFalse(finishResult.isError());
        assertTrue(finishResult.isFinished());
        verify(projectCommandRunner).runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                eq("lint")
        );
        verify(projectCommandRunner).runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                eq("build")
        );
    }

    @Test
    void writeAfterCheckProjectRequiresNewCheckBeforeFinish() {
        ProjectCommandRunner projectCommandRunner = mock(ProjectCommandRunner.class);
        when(projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                eq("lint")
        )).thenReturn(passed("lint"));
        when(projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                eq("build")
        )).thenReturn(passed("build"));
        WorkspaceToolExecutor executor = new WorkspaceToolExecutor(
                workspacePath,
                objectMapper,
                projectCommandRunner,
                1L,
                2L
        );

        WorkspaceToolResult checkResult = executor.execute(ToolExecutionRequest.builder()
                .name("checkProject")
                .arguments("{}")
                .build());
        WorkspaceToolResult writeResult = executor.execute(ToolExecutionRequest.builder()
                .name("writeFile")
                .arguments("{\"path\":\"src/pages/IndexPage.jsx\",\"content\":\"export default function IndexPage() { return <div /> }\"}")
                .build());
        WorkspaceToolResult finishResult = executor.execute(ToolExecutionRequest.builder()
                .name("finish")
                .arguments("{\"summary\":\"done\"}")
                .build());

        assertFalse(checkResult.isError());
        assertFalse(writeResult.isError());
        assertTrue(finishResult.isError());
    }

    @Test
    void failedLintDoesNotRunBuildOrAllowFinish() {
        ProjectCommandRunner projectCommandRunner = mock(ProjectCommandRunner.class);
        when(projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                eq("lint")
        )).thenReturn(failed("lint"));
        WorkspaceToolExecutor executor = new WorkspaceToolExecutor(
                workspacePath,
                objectMapper,
                projectCommandRunner,
                1L,
                2L
        );

        WorkspaceToolResult checkResult = executor.execute(ToolExecutionRequest.builder()
                .name("checkProject")
                .arguments("{}")
                .build());
        WorkspaceToolResult finishResult = executor.execute(ToolExecutionRequest.builder()
                .name("finish")
                .arguments("{\"summary\":\"done\"}")
                .build());

        assertTrue(checkResult.isError());
        assertTrue(finishResult.isError());
        verify(projectCommandRunner, never()).runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                eq("build")
        );
    }

    private ProjectCommandResult passed(String command) {
        return command(command, true, 0);
    }

    private ProjectCommandResult failed(String command) {
        return command(command, false, 1);
    }

    private ProjectCommandResult command(String command, boolean success, int exitCode) {
        String commandText = "pnpm.cmd " + command;
        return ProjectCommandResult.builder()
                .command(new String[]{"pnpm.cmd", command})
                .commandText(commandText)
                .content("$ " + commandText + "\n" + command + " output")
                .exitCode(exitCode)
                .success(success)
                .errorMessage(success ? null : "命令执行失败：" + commandText + "，退出码：" + exitCode)
                .build();
    }
}
