package org.ymz.app.ai.codegen.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskEventRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationMessageRecorder;

import java.nio.file.Path;
import java.util.Arrays;
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
import static org.mockito.Mockito.verify;

class CodeGenerationCommandRunnerTest {

    @TempDir
    Path workspacePath;

    @Test
    void runCommandPublishesCommandEventsAndStreamsLiveOutput() {
        CodeGenerationMessageRecorder appTaskLogPublisher = mock(CodeGenerationMessageRecorder.class);
        CodeGenerationTaskEventRecorder agentRunEventPublisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationCommandRunner runner = new CodeGenerationCommandRunner(appTaskLogPublisher, agentRunEventPublisher);
        String[] command = successCommand();

        runner.runCommand(1L, 2L, workspacePath, command);

        verify(agentRunEventPublisher).publishCommandStarted(1L, 2L, String.join(" ", command));
        ArgumentCaptor<CodeGenerationCommandResult> resultCaptor = ArgumentCaptor.forClass(CodeGenerationCommandResult.class);
        verify(agentRunEventPublisher).publishCommandFinished(eq(1L), eq(2L), resultCaptor.capture());
        assertTrue(resultCaptor.getValue().isSuccess());
        assertEquals(Arrays.asList(command), Arrays.asList(resultCaptor.getValue().getCommand()));
        assertNotNull(resultCaptor.getValue().getDurationMillis());

        ArgumentCaptor<String> transientContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(appTaskLogPublisher, atLeast(3)).publishTransientMessage(
                eq(1L),
                eq(2L),
                transientContentCaptor.capture(),
                anyMap());
        assertTrue(transientContentCaptor.getAllValues().contains("$ " + String.join(" ", command)));
        assertTrue(transientContentCaptor.getAllValues().stream().anyMatch(item -> item.contains("first-line")));
        assertTrue(transientContentCaptor.getAllValues().stream().anyMatch(item -> item.contains("second-line")));
    }

    @Test
    void runCommandPublishesFailureCommandEventBeforeThrowing() {
        CodeGenerationMessageRecorder appTaskLogPublisher = mock(CodeGenerationMessageRecorder.class);
        CodeGenerationTaskEventRecorder agentRunEventPublisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationCommandRunner runner = new CodeGenerationCommandRunner(appTaskLogPublisher, agentRunEventPublisher);
        String[] command = failureCommand();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> runner.runCommand(1L, 2L, workspacePath, command));
        assertTrue(exception.getMessage().contains("命令执行失败"));

        ArgumentCaptor<CodeGenerationCommandResult> resultCaptor = ArgumentCaptor.forClass(CodeGenerationCommandResult.class);
        verify(agentRunEventPublisher).publishCommandFinished(eq(1L), eq(2L), resultCaptor.capture());
        assertFalse(resultCaptor.getValue().isSuccess());
        assertEquals(7, resultCaptor.getValue().getExitCode());
        assertTrue(resultCaptor.getValue().getContent().contains("failed-line"));

        ArgumentCaptor<String> transientContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(appTaskLogPublisher, atLeast(2)).publishTransientMessage(
                eq(1L),
                eq(2L),
                transientContentCaptor.capture(),
                anyMap());
        assertTrue(transientContentCaptor.getAllValues().stream().anyMatch(item -> item.contains("failed-line")));
        assertFalse(transientContentCaptor.getAllValues().isEmpty());
    }

    @Test
    void runCommandResultReturnsFailureWithoutThrowing() {
        CodeGenerationMessageRecorder appTaskLogPublisher = mock(CodeGenerationMessageRecorder.class);
        CodeGenerationTaskEventRecorder agentRunEventPublisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationCommandRunner runner = new CodeGenerationCommandRunner(appTaskLogPublisher, agentRunEventPublisher);
        String[] command = failureCommand();

        CodeGenerationCommandResult result = runner.runCommandResult(1L, 2L, workspacePath, command);

        assertFalse(result.isSuccess());
        assertEquals(Arrays.asList(command), Arrays.asList(result.getCommand()));
        assertEquals(String.join(" ", command), result.getCommandText());
        assertEquals(7, result.getExitCode());
        assertTrue(result.getContent().contains("failed-line"));
        assertTrue(result.getErrorMessage().contains("退出码：7"));
        verify(agentRunEventPublisher).publishCommandFinished(eq(1L), eq(2L), eq(result));
    }

    @Test
    void summaryModeStillStreamsLiveOutputAndPublishesCommandResult() {
        CodeGenerationMessageRecorder appTaskLogPublisher = mock(CodeGenerationMessageRecorder.class);
        CodeGenerationTaskEventRecorder agentRunEventPublisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationCommandRunner runner = new CodeGenerationCommandRunner(appTaskLogPublisher, agentRunEventPublisher);
        String[] command = successCommand();

        CodeGenerationCommandResult result = runner.runCommandResult(
                1L,
                2L,
                workspacePath,
                command,
                CodeGenerationCommandRunner.LogMode.SUMMARY);

        assertTrue(result.isSuccess());
        verify(agentRunEventPublisher).publishCommandFinished(eq(1L), eq(2L), eq(result));
        verify(appTaskLogPublisher, atLeast(3)).publishTransientMessage(
                eq(1L),
                eq(2L),
                anyString(),
                anyMap());
    }

    @Test
    void transientOnlyModeStillStreamsLiveOutputAndPublishesCommandResult() {
        CodeGenerationMessageRecorder appTaskLogPublisher = mock(CodeGenerationMessageRecorder.class);
        CodeGenerationTaskEventRecorder agentRunEventPublisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationCommandRunner runner = new CodeGenerationCommandRunner(appTaskLogPublisher, agentRunEventPublisher);
        String[] command = successCommand();

        CodeGenerationCommandResult result = runner.runCommandResult(
                1L,
                2L,
                workspacePath,
                command,
                CodeGenerationCommandRunner.LogMode.TRANSIENT_ONLY);

        assertTrue(result.isSuccess());
        verify(agentRunEventPublisher).publishCommandFinished(eq(1L), eq(2L), eq(result));
        verify(appTaskLogPublisher, atLeast(3)).publishTransientMessage(
                eq(1L),
                eq(2L),
                anyString(),
                anyMap());
    }

    private String[] successCommand() {
        if (isWindows()) {
            return new String[] { "cmd", "/c", "echo first-line && echo second-line" };
        }
        return new String[] { "sh", "-c", "printf 'first-line\\nsecond-line\\n'" };
    }

    private String[] failureCommand() {
        if (isWindows()) {
            return new String[] { "cmd", "/c", "echo failed-line && exit /b 7" };
        }
        return new String[] { "sh", "-c", "echo failed-line; exit 7" };
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

}
