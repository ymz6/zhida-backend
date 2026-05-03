package org.ymz.app.service.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.ymz.app.ai.agent.CodeGenerationAgent;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppCreateTaskRunnerTest {

    @TempDir
    Path workspacePath;

    @Test
    void runCreateTaskRepairsLintFailureThenSucceeds() throws Exception {
        Fixture fixture = fixture();
        ProjectCommandResult lintFailed = failed("lint");
        ProjectCommandResult lintPassed = passed("lint");
        ProjectCommandResult buildPassed = passed("build");
        when(fixture.projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                any(String[].class)
        ))
                .thenReturn(lintFailed, lintPassed, buildPassed);

        fixture.runner.runCreateTask(2L);

        verify(fixture.projectCommandRunner).runPnpmCommand(
                1L,
                2L,
                workspacePath,
                ProjectCommandRunner.LogMode.SUMMARY,
                "install",
                "--frozen-lockfile"
        );
        InOrder inOrder = inOrder(fixture.projectCommandRunner, fixture.codeGenerationAgent);
        inOrder.verify(fixture.projectCommandRunner).runPnpmCommand(
                1L,
                2L,
                workspacePath,
                ProjectCommandRunner.LogMode.SUMMARY,
                "install",
                "--frozen-lockfile"
        );
        inOrder.verify(fixture.codeGenerationAgent).generate(fixture.app, fixture.task, workspacePath);
        verify(fixture.codeGenerationAgent).repair(fixture.app, fixture.task, workspacePath, lintFailed, 1);
        verify(fixture.projectWorkspaceManager).publishPreview(1L, workspacePath);

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        assertTrue(appCaptor.getAllValues().stream()
                .anyMatch(app -> AppStatus.READY.name().equals(app.getStatus())));
    }

    @Test
    void runCreateTaskRestartsLintAfterBuildRepair() throws Exception {
        Fixture fixture = fixture();
        ProjectCommandResult lintPassed = passed("lint");
        ProjectCommandResult buildFailed = failed("build");
        ProjectCommandResult secondLintPassed = passed("lint");
        ProjectCommandResult buildPassed = passed("build");
        when(fixture.projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                any(String[].class)
        ))
                .thenReturn(lintPassed, buildFailed, secondLintPassed, buildPassed);

        fixture.runner.runCreateTask(2L);

        verify(fixture.codeGenerationAgent).repair(fixture.app, fixture.task, workspacePath, buildFailed, 1);
        ArgumentCaptor<String[]> argsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(fixture.projectCommandRunner, times(4)).runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                argsCaptor.capture()
        );
        List<String[]> commands = argsCaptor.getAllValues();
        assertEquals("lint", commands.get(0)[0]);
        assertEquals("build", commands.get(1)[0]);
        assertEquals("lint", commands.get(2)[0]);
        assertEquals("build", commands.get(3)[0]);
    }

    @Test
    void runCreateTaskFailsAfterThreeRepairAttempts() throws Exception {
        Fixture fixture = fixture();
        ProjectCommandResult firstFailure = failed("lint");
        ProjectCommandResult secondFailure = failed("lint");
        ProjectCommandResult thirdFailure = failed("lint");
        ProjectCommandResult finalFailure = failed("lint");
        when(fixture.projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                any(String[].class)
        ))
                .thenReturn(firstFailure, secondFailure, thirdFailure, finalFailure);

        fixture.runner.runCreateTask(2L);

        verify(fixture.codeGenerationAgent, times(1)).repair(fixture.app, fixture.task, workspacePath, firstFailure, 1);
        verify(fixture.codeGenerationAgent, times(1)).repair(fixture.app, fixture.task, workspacePath, secondFailure, 2);
        verify(fixture.codeGenerationAgent, times(1)).repair(fixture.app, fixture.task, workspacePath, thirdFailure, 3);
        verify(fixture.projectWorkspaceManager, never()).publishPreview(1L, workspacePath);

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        assertTrue(appCaptor.getAllValues().stream()
                .anyMatch(app -> AppStatus.FAILED.name().equals(app.getStatus())));
    }

    private Fixture fixture() throws Exception {
        AppService appService = mock(AppService.class);
        AppTaskService appTaskService = mock(AppTaskService.class);
        AppTaskLogPublisher appTaskLogPublisher = mock(AppTaskLogPublisher.class);
        AppTaskSseBroker appTaskSseBroker = mock(AppTaskSseBroker.class);
        ProjectWorkspaceManager projectWorkspaceManager = mock(ProjectWorkspaceManager.class);
        CodeGenerationAgent codeGenerationAgent = mock(CodeGenerationAgent.class);
        ProjectCommandRunner projectCommandRunner = mock(ProjectCommandRunner.class);
        AppTaskMetrics appTaskMetrics = mock(AppTaskMetrics.class);
        App app = App.builder()
                .id(1L)
                .name("测试应用")
                .build();
        AppTask task = AppTask.builder()
                .id(2L)
                .appId(1L)
                .prompt("做一个任务看板")
                .build();

        when(appTaskService.getById(2L)).thenReturn(task);
        when(appService.getById(1L)).thenReturn(app);
        when(projectWorkspaceManager.initializeWorkspace(1L)).thenReturn(workspacePath);
        when(projectWorkspaceManager.publishPreview(1L, workspacePath)).thenReturn("/previews/1/index.html");

        AppCreateTaskRunner runner = new AppCreateTaskRunner(
                appService,
                appTaskService,
                appTaskLogPublisher,
                appTaskSseBroker,
                projectWorkspaceManager,
                codeGenerationAgent,
                projectCommandRunner,
                appTaskMetrics
        );

        return new Fixture(
                runner,
                appService,
                appTaskService,
                appTaskLogPublisher,
                appTaskSseBroker,
                projectWorkspaceManager,
                codeGenerationAgent,
                projectCommandRunner,
                appTaskMetrics,
                app,
                task
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

    private record Fixture(
            AppCreateTaskRunner runner,
            AppService appService,
            AppTaskService appTaskService,
            AppTaskLogPublisher appTaskLogPublisher,
            AppTaskSseBroker appTaskSseBroker,
            ProjectWorkspaceManager projectWorkspaceManager,
            CodeGenerationAgent codeGenerationAgent,
            ProjectCommandRunner projectCommandRunner,
            AppTaskMetrics appTaskMetrics,
            App app,
            AppTask task
    ) {
    }
}
