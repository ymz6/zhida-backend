package org.ymz.app.service.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProjectCommandRunnerTest {

    @TempDir
    Path workspacePath;

    @Test
    void runCommandPersistsOneAggregatedBuildLogAndStreamsLiveOutput() {
        AppTaskLogPublisher appTaskLogPublisher = mock(AppTaskLogPublisher.class);
        ProjectCommandRunner runner = new ProjectCommandRunner(appTaskLogPublisher);
        String[] command = successCommand();

        runner.runCommand(1L, 2L, workspacePath, command);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> metadataCaptor = metadataCaptor();
        verify(appTaskLogPublisher, times(1)).appendMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.BUILD_LOG),
                contentCaptor.capture(),
                metadataCaptor.capture()
        );

        String content = contentCaptor.getValue();
        assertTrue(content.startsWith("$ " + String.join(" ", command)));
        assertTrue(content.contains("first-line"));
        assertTrue(content.contains("second-line"));

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertEquals(Arrays.asList(command), metadata.get("command"));
        assertEquals(0, metadata.get("exitCode"));
        assertEquals(true, metadata.get("success"));
        assertEquals(false, metadata.get("timedOut"));
        assertNotNull(metadata.get("durationMillis"));

        ArgumentCaptor<String> transientContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(appTaskLogPublisher, atLeast(3)).publishTransientMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.BUILD_LOG),
                transientContentCaptor.capture(),
                anyMap()
        );
        assertTrue(transientContentCaptor.getAllValues().contains("$ " + String.join(" ", command)));
        assertTrue(transientContentCaptor.getAllValues().stream().anyMatch(item -> item.contains("first-line")));
        assertTrue(transientContentCaptor.getAllValues().stream().anyMatch(item -> item.contains("second-line")));
    }

    @Test
    void runCommandPersistsOneAggregatedBuildLogBeforeThrowingOnFailure() {
        AppTaskLogPublisher appTaskLogPublisher = mock(AppTaskLogPublisher.class);
        ProjectCommandRunner runner = new ProjectCommandRunner(appTaskLogPublisher);
        String[] command = failureCommand();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> runner.runCommand(1L, 2L, workspacePath, command)
        );
        assertTrue(exception.getMessage().contains("命令执行失败"));

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> metadataCaptor = metadataCaptor();
        verify(appTaskLogPublisher, times(1)).appendMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.BUILD_LOG),
                contentCaptor.capture(),
                metadataCaptor.capture()
        );

        assertTrue(contentCaptor.getValue().startsWith("$ " + String.join(" ", command)));
        assertTrue(contentCaptor.getValue().contains("failed-line"));

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertEquals(Arrays.asList(command), metadata.get("command"));
        assertEquals(7, metadata.get("exitCode"));
        assertEquals(false, metadata.get("success"));
        assertEquals(false, metadata.get("timedOut"));
        assertTrue(String.valueOf(metadata.get("errorMessage")).contains("退出码：7"));

        ArgumentCaptor<String> transientContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(appTaskLogPublisher, atLeast(2)).publishTransientMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.BUILD_LOG),
                transientContentCaptor.capture(),
                anyMap()
        );
        assertTrue(transientContentCaptor.getAllValues().stream().anyMatch(item -> item.contains("failed-line")));
        assertFalse(transientContentCaptor.getAllValues().isEmpty());
    }

    @Test
    void runCommandResultReturnsFailureWithoutThrowing() {
        AppTaskLogPublisher appTaskLogPublisher = mock(AppTaskLogPublisher.class);
        ProjectCommandRunner runner = new ProjectCommandRunner(appTaskLogPublisher);
        String[] command = failureCommand();

        ProjectCommandResult result = runner.runCommandResult(1L, 2L, workspacePath, command);

        assertFalse(result.isSuccess());
        assertEquals(Arrays.asList(command), Arrays.asList(result.getCommand()));
        assertEquals(String.join(" ", command), result.getCommandText());
        assertEquals(7, result.getExitCode());
        assertTrue(result.getContent().contains("failed-line"));
        assertTrue(result.getErrorMessage().contains("退出码：7"));
        verify(appTaskLogPublisher, times(1)).appendMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.BUILD_LOG),
                eq(result.getContent()),
                anyMap()
        );
    }

    @Test
    void summaryModePersistsCommandSummaryAndStreamsLiveOutput() {
        AppTaskLogPublisher appTaskLogPublisher = mock(AppTaskLogPublisher.class);
        ProjectCommandRunner runner = new ProjectCommandRunner(appTaskLogPublisher);
        String[] command = successCommand();

        ProjectCommandResult result = runner.runCommandResult(
                1L,
                2L,
                workspacePath,
                command,
                ProjectCommandRunner.LogMode.SUMMARY
        );

        assertTrue(result.isSuccess());
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(appTaskLogPublisher, times(1)).appendMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.BUILD_LOG),
                contentCaptor.capture(),
                anyMap()
        );
        assertTrue(contentCaptor.getValue().contains("命令执行成功"));
        assertFalse(contentCaptor.getValue().contains("\nfirst-line"));
        verify(appTaskLogPublisher, atLeast(3)).publishTransientMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.BUILD_LOG),
                anyString(),
                anyMap()
        );
    }

    @Test
    void transientOnlyModeDoesNotPersistBuildLog() {
        AppTaskLogPublisher appTaskLogPublisher = mock(AppTaskLogPublisher.class);
        ProjectCommandRunner runner = new ProjectCommandRunner(appTaskLogPublisher);
        String[] command = successCommand();

        ProjectCommandResult result = runner.runCommandResult(
                1L,
                2L,
                workspacePath,
                command,
                ProjectCommandRunner.LogMode.TRANSIENT_ONLY
        );

        assertTrue(result.isSuccess());
        verify(appTaskLogPublisher, times(0)).appendMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.BUILD_LOG),
                anyString(),
                anyMap()
        );
        verify(appTaskLogPublisher, atLeast(3)).publishTransientMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.BUILD_LOG),
                anyString(),
                anyMap()
        );
    }

    private String[] successCommand() {
        if (isWindows()) {
            return new String[]{"cmd", "/c", "echo first-line && echo second-line"};
        }
        return new String[]{"sh", "-c", "printf 'first-line\\nsecond-line\\n'"};
    }

    private String[] failureCommand() {
        if (isWindows()) {
            return new String[]{"cmd", "/c", "echo failed-line && exit /b 7"};
        }
        return new String[]{"sh", "-c", "echo failed-line; exit 7"};
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> metadataCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
