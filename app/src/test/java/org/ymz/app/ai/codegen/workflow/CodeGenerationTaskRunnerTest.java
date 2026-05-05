package org.ymz.app.ai.codegen.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.ymz.app.ai.codegen.event.CodeGenerationMessageRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskEventRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskSseBroker;
import org.ymz.app.ai.codegen.memory.AppContextSummaryManager;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandRunner;
import org.ymz.app.ai.codegen.workspace.CodeGenerationProjectVerifier;
import org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceManager;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeGenerationTaskRunnerTest {

    @TempDir
    Path workspacePath;

    @Test
    void runCreateTaskRepairsValidationFailureThenSucceeds() throws Exception {
        Fixture fixture = fixture(AppTaskType.CREATE, null);
        CodeGenerationCommandResult lintFailed = failed("lint");
        when(fixture.projectVerifier.verify(1L, 2L, workspacePath))
                .thenReturn(lintFailed)
                .thenReturn(null);

        fixture.runner.runTask(2L);

        InOrder inOrder = inOrder(fixture.projectCommandRunner, fixture.agentExecutor);
        inOrder.verify(fixture.projectCommandRunner).runPnpmCommand(
                1L,
                2L,
                workspacePath,
                CodeGenerationCommandRunner.LogMode.SUMMARY,
                "install",
                "--frozen-lockfile"
        );
        inOrder.verify(fixture.agentExecutor).generate(fixture.app, fixture.task, workspacePath);
        verify(fixture.agentExecutor).repair(fixture.app, fixture.task, workspacePath, lintFailed, 1);
        verify(fixture.projectWorkspaceManager).refreshPreview(1L, workspacePath);

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        assertTrue(appCaptor.getAllValues().stream()
                .anyMatch(app -> AppStatus.READY.name().equals(app.getStatus())));
    }

    @Test
    void runIterationTaskSucceedsAndPublishesNewPreview() throws Exception {
        Files.createDirectories(workspacePath.resolve("node_modules"));
        Fixture fixture = fixture(AppTaskType.ITERATE, "/previews/1/old.html");
        when(fixture.projectVerifier.verify(1L, 2L, workspacePath)).thenReturn(null);

        fixture.runner.runTask(2L);

        verify(fixture.projectCommandRunner, never()).runPnpmCommand(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(CodeGenerationCommandRunner.LogMode.SUMMARY),
                eq("install"),
                eq("--frozen-lockfile")
        );
        verify(fixture.agentExecutor).iterate(fixture.app, fixture.task, workspacePath);
        verify(fixture.projectWorkspaceManager).refreshPreview(1L, workspacePath);

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        assertTrue(appCaptor.getAllValues().stream()
                .anyMatch(app -> AppStatus.ITERATING.name().equals(app.getStatus())));
        assertTrue(appCaptor.getAllValues().stream()
                .anyMatch(app -> AppStatus.READY.name().equals(app.getStatus())
                        && "/previews/1/index.html".equals(app.getPreviewUrl())));
    }

    @Test
    void runIterationTaskFailsBackToReadyWhenOldPreviewExists() throws Exception {
        Files.createDirectories(workspacePath.resolve("node_modules"));
        Fixture fixture = fixture(AppTaskType.ITERATE, "/previews/1/old.html");
        CodeGenerationCommandResult firstFailure = failed("lint");
        CodeGenerationCommandResult secondFailure = failed("lint");
        CodeGenerationCommandResult thirdFailure = failed("lint");
        CodeGenerationCommandResult finalFailure = failed("lint");
        when(fixture.projectVerifier.verify(1L, 2L, workspacePath))
                .thenReturn(firstFailure, secondFailure, thirdFailure, finalFailure);

        fixture.runner.runTask(2L);

        verify(fixture.agentExecutor, times(1)).repair(fixture.app, fixture.task, workspacePath, firstFailure, 1);
        verify(fixture.agentExecutor, times(1)).repair(fixture.app, fixture.task, workspacePath, secondFailure, 2);
        verify(fixture.agentExecutor, times(1)).repair(fixture.app, fixture.task, workspacePath, thirdFailure, 3);
        verify(fixture.projectWorkspaceManager, never()).refreshPreview(1L, workspacePath);

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        assertTrue(appCaptor.getAllValues().stream()
                .anyMatch(app -> AppStatus.READY.name().equals(app.getStatus())));
    }

    private Fixture fixture(AppTaskType taskType, String previewUrl) throws Exception {
        AppService appService = mock(AppService.class);
        AppTaskService appTaskService = mock(AppTaskService.class);
        CodeGenerationMessageRecorder messageRecorder = mock(CodeGenerationMessageRecorder.class);
        CodeGenerationTaskEventRecorder taskEventRecorder = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationTaskSseBroker taskSseBroker = mock(CodeGenerationTaskSseBroker.class);
        CodeGenerationWorkspaceManager projectWorkspaceManager = mock(CodeGenerationWorkspaceManager.class);
        CodeGenerationAgentExecutor agentExecutor = mock(CodeGenerationAgentExecutor.class);
        AppContextSummaryManager appContextSummaryManager = mock(AppContextSummaryManager.class);
        CodeGenerationCommandRunner projectCommandRunner = mock(CodeGenerationCommandRunner.class);
        CodeGenerationProjectVerifier projectVerifier = mock(CodeGenerationProjectVerifier.class);
        AppTaskMetrics appTaskMetrics = mock(AppTaskMetrics.class);
        App app = App.builder()
                .id(1L)
                .name("测试应用")
                .workspacePath(taskType == AppTaskType.ITERATE ? workspacePath.toString() : null)
                .previewUrl(previewUrl)
                .build();
        AppTask task = AppTask.builder()
                .id(2L)
                .appId(1L)
                .taskType(taskType.name())
                .prompt("做一个任务看板")
                .build();

        when(appTaskService.getById(2L)).thenReturn(task);
        when(appService.getById(1L)).thenReturn(app);
        when(projectWorkspaceManager.initializeWorkspace(1L)).thenReturn(workspacePath);
        when(projectWorkspaceManager.refreshPreview(1L, workspacePath)).thenReturn("/previews/1/index.html");

        CodeGenerationTaskRunner runner = new CodeGenerationTaskRunner(
                appService,
                appTaskService,
                messageRecorder,
                taskEventRecorder,
                taskSseBroker,
                projectWorkspaceManager,
                agentExecutor,
                appContextSummaryManager,
                projectCommandRunner,
                projectVerifier,
                appTaskMetrics
        );

        return new Fixture(
                runner,
                appService,
                projectCommandRunner,
                projectVerifier,
                projectWorkspaceManager,
                agentExecutor,
                app,
                task
        );
    }

    private CodeGenerationCommandResult failed(String command) {
        String commandText = "pnpm.cmd " + command;
        return CodeGenerationCommandResult.builder()
                .command(new String[]{"pnpm.cmd", command})
                .commandText(commandText)
                .content("$ " + commandText + "\n" + command + " output")
                .exitCode(1)
                .success(false)
                .errorMessage("命令执行失败：" + commandText + "，退出码：1")
                .build();
    }

    private record Fixture(
            CodeGenerationTaskRunner runner,
            AppService appService,
            CodeGenerationCommandRunner projectCommandRunner,
            CodeGenerationProjectVerifier projectVerifier,
            CodeGenerationWorkspaceManager projectWorkspaceManager,
            CodeGenerationAgentExecutor agentExecutor,
            App app,
            AppTask task
    ) {
    }
}
