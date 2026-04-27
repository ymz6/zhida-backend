package org.ymz.app.service.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ymz.app.ai.agent.CodeGenerationAgent;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppIterationTaskRunnerTest {

    @TempDir
    Path workspacePath;

    @Test
    void runIterationTaskSucceedsAndPublishesNewPreview() throws Exception {
        Fixture fixture = fixture("/previews/1/old.html");
        when(fixture.projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                any(String[].class)
        )).thenReturn(passed("lint"), passed("build"));
        when(fixture.projectWorkspaceManager.publishPreview(1L, workspacePath))
                .thenReturn("/previews/1/index.html");

        fixture.runner.runIterationTask(2L);

        verify(fixture.projectCommandRunner, never()).runPnpmCommand(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                any(String[].class)
        );
        verify(fixture.codeGenerationAgent).iterate(fixture.app, fixture.task, workspacePath);
        verify(fixture.projectWorkspaceManager).publishPreview(1L, workspacePath);

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
        Fixture fixture = fixture("/previews/1/old.html");
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
        )).thenReturn(firstFailure, secondFailure, thirdFailure, finalFailure);

        fixture.runner.runIterationTask(2L);

        verify(fixture.codeGenerationAgent, times(1)).repair(fixture.app, fixture.task, workspacePath, firstFailure, 1);
        verify(fixture.codeGenerationAgent, times(1)).repair(fixture.app, fixture.task, workspacePath, secondFailure, 2);
        verify(fixture.codeGenerationAgent, times(1)).repair(fixture.app, fixture.task, workspacePath, thirdFailure, 3);
        verify(fixture.projectWorkspaceManager, never()).publishPreview(1L, workspacePath);

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        assertTrue(appCaptor.getAllValues().stream()
                .anyMatch(app -> AppStatus.READY.name().equals(app.getStatus())));
    }

    @Test
    void runIterationTaskFailsToFailedWhenNoOldPreviewExists() throws Exception {
        Fixture fixture = fixture(null);
        when(fixture.projectCommandRunner.runPnpmCommandResult(
                eq(1L),
                eq(2L),
                eq(workspacePath),
                eq(ProjectCommandRunner.LogMode.SUMMARY),
                any(String[].class)
        )).thenReturn(failed("lint"), failed("lint"), failed("lint"), failed("lint"));

        fixture.runner.runIterationTask(2L);

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        assertTrue(appCaptor.getAllValues().stream()
                .anyMatch(app -> AppStatus.FAILED.name().equals(app.getStatus())));
    }

    private Fixture fixture(String previewUrl) throws Exception {
        Files.createDirectories(workspacePath.resolve("node_modules"));
        AppService appService = mock(AppService.class);
        AppTaskService appTaskService = mock(AppTaskService.class);
        AppTaskLogPublisher appTaskLogPublisher = mock(AppTaskLogPublisher.class);
        AppTaskSseBroker appTaskSseBroker = mock(AppTaskSseBroker.class);
        ProjectWorkspaceManager projectWorkspaceManager = mock(ProjectWorkspaceManager.class);
        CodeGenerationAgent codeGenerationAgent = mock(CodeGenerationAgent.class);
        ProjectCommandRunner projectCommandRunner = mock(ProjectCommandRunner.class);
        App app = App.builder()
                .id(1L)
                .name("测试应用")
                .workspacePath(workspacePath.toString())
                .previewUrl(previewUrl)
                .build();
        AppTask task = AppTask.builder()
                .id(2L)
                .appId(1L)
                .prompt("加一个筛选面板")
                .build();

        when(appTaskService.getById(2L)).thenReturn(task);
        when(appService.getById(1L)).thenReturn(app);

        AppIterationTaskRunner runner = new AppIterationTaskRunner(
                appService,
                appTaskService,
                appTaskLogPublisher,
                appTaskSseBroker,
                projectWorkspaceManager,
                codeGenerationAgent,
                projectCommandRunner
        );
        return new Fixture(
                runner,
                appService,
                appTaskService,
                projectWorkspaceManager,
                codeGenerationAgent,
                projectCommandRunner,
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
            AppIterationTaskRunner runner,
            AppService appService,
            AppTaskService appTaskService,
            ProjectWorkspaceManager projectWorkspaceManager,
            CodeGenerationAgent codeGenerationAgent,
            ProjectCommandRunner projectCommandRunner,
            App app,
            AppTask task
    ) {
    }
}
