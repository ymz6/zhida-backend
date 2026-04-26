package org.ymz.app.service.generation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.agent.CodeGenerationAgent;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskStep;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 编排已启动的 CREATE 任务执行流程。
 *
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppCreateTaskRunner {

    private static final int MAX_REPAIR_ATTEMPTS = 3;

    private final AppService appService;
    private final AppTaskService appTaskService;
    private final AppTaskLogPublisher appTaskLogPublisher;
    private final AppTaskSseBroker appTaskSseBroker;
    private final ProjectWorkspaceManager projectWorkspaceManager;
    private final CodeGenerationAgent codeGenerationAgent;
    private final ProjectCommandRunner projectCommandRunner;

    public void runCreateTask(Long taskId) {
        AppTask task = appTaskService.getById(taskId);
        if (task == null) {
            return;
        }
        App app = appService.getById(task.getAppId());
        if (app == null) {
            failTask(task, null, "应用不存在");
            return;
        }

        try {
            appTaskLogPublisher.appendMessage(
                    app.getId(),
                    task.getId(),
                    AppChatMessageRole.SYSTEM,
                    AppChatMessageType.CHAT,
                    "任务已启动，正在初始化项目模板"
            );

            Path workspacePath = projectWorkspaceManager.initializeWorkspace(app.getId());
            updateApp(App.builder()
                    .id(app.getId())
                    .workspacePath(workspacePath.toString())
                    .status(AppStatus.GENERATING.name())
                    .build());
            updateTask(task.getId(), AppTaskStatus.RUNNING, AppTaskStep.GENERATING_CODE, null, null, null);
            task = appTaskService.getById(task.getId());
            app = appService.getById(app.getId());
            appTaskLogPublisher.publishState(task);
            appTaskLogPublisher.appendMessage(
                    app.getId(),
                    task.getId(),
                    AppChatMessageRole.SYSTEM,
                    AppChatMessageType.CHAT,
                    "项目模板已初始化，正在安装依赖",
                    Map.of("workspacePath", workspacePath.toString())
            );
            projectCommandRunner.runPnpmCommand(
                    app.getId(),
                    task.getId(),
                    workspacePath,
                    ProjectCommandRunner.LogMode.SUMMARY,
                    "install",
                    "--frozen-lockfile"
            );

            appTaskLogPublisher.appendMessage(
                    app.getId(),
                    task.getId(),
                    AppChatMessageRole.SYSTEM,
                    AppChatMessageType.CHAT,
                    "依赖安装完成，Agent 开始改造应用"
            );

            codeGenerationAgent.generate(app, task, workspacePath);

            updateApp(App.builder()
                    .id(app.getId())
                    .status(AppStatus.BUILDING.name())
                    .build());
            updateTask(task.getId(), AppTaskStatus.RUNNING, AppTaskStep.BUILDING, null, null, null);
            task = appTaskService.getById(task.getId());
            appTaskLogPublisher.publishState(task);

            verifyProject(app, task, workspacePath);

            String previewUrl = projectWorkspaceManager.publishPreview(app.getId(), workspacePath);
            updateApp(App.builder()
                    .id(app.getId())
                    .previewUrl(previewUrl)
                    .status(AppStatus.READY.name())
                    .errorMessage("")
                    .build());
            updateTask(task.getId(), AppTaskStatus.SUCCESS, AppTaskStep.FINISHED, "应用生成成功", null, LocalDateTime.now());
            task = appTaskService.getById(task.getId());
            appTaskLogPublisher.publishState(task);
            appTaskLogPublisher.appendMessage(
                    app.getId(),
                    task.getId(),
                    AppChatMessageRole.SYSTEM,
                    AppChatMessageType.CHAT,
                    "应用生成完成",
                    Map.of("previewUrl", previewUrl)
            );
        } catch (Exception e) {
            log.error("Create app task failed, taskId={}", taskId, e);
            failTask(task, app, e.getMessage());
        } finally {
            appTaskSseBroker.complete(taskId);
        }
    }

    private void verifyProject(App app, AppTask task, Path workspacePath) {
        for (int repairCount = 0; repairCount <= MAX_REPAIR_ATTEMPTS; repairCount++) {
            ProjectCommandResult failedCommand = verifyProjectOnce(app, task, workspacePath);
            if (failedCommand == null) {
                return;
            }

            if (repairCount == MAX_REPAIR_ATTEMPTS) {
                throw new IllegalStateException("应用校验失败，已自动修复 "
                        + MAX_REPAIR_ATTEMPTS + " 轮仍未通过：" + failedCommand.getErrorMessage());
            }

            int repairAttempt = repairCount + 1;
            appTaskLogPublisher.appendMessage(
                    app.getId(),
                    task.getId(),
                    AppChatMessageRole.SYSTEM,
                    AppChatMessageType.CHAT,
                    "校验失败，Agent 正在进行第 " + repairAttempt + " 轮自动修复",
                    Map.of(
                            "repairAttempt", repairAttempt,
                            "failedCommand", failedCommand.getCommandText()
                    )
            );
            codeGenerationAgent.repair(app, task, workspacePath, failedCommand, repairAttempt);
        }
    }

    private ProjectCommandResult verifyProjectOnce(App app, AppTask task, Path workspacePath) {
        ProjectCommandResult lintResult = projectCommandRunner.runPnpmCommandResult(
                app.getId(),
                task.getId(),
                workspacePath,
                ProjectCommandRunner.LogMode.SUMMARY,
                "lint"
        );
        if (!lintResult.isSuccess()) {
            return lintResult;
        }

        ProjectCommandResult buildResult = projectCommandRunner.runPnpmCommandResult(
                app.getId(),
                task.getId(),
                workspacePath,
                ProjectCommandRunner.LogMode.SUMMARY,
                "build"
        );
        if (!buildResult.isSuccess()) {
            return buildResult;
        }
        return null;
    }

    private void failTask(AppTask task, App app, String errorMessage) {
        String message = errorMessage == null || errorMessage.isBlank() ? "应用生成失败" : errorMessage;
        if (app != null) {
            updateApp(App.builder()
                    .id(app.getId())
                    .status(AppStatus.FAILED.name())
                    .errorMessage(message)
                    .build());
        }
        updateTask(task.getId(), AppTaskStatus.FAILED, AppTaskStep.FINISHED, null, message, LocalDateTime.now());
        AppTask latestTask = appTaskService.getById(task.getId());
        appTaskLogPublisher.publishState(latestTask);
        appTaskLogPublisher.appendMessage(
                task.getAppId(),
                task.getId(),
                AppChatMessageRole.SYSTEM,
                AppChatMessageType.ERROR,
                message
        );
    }

    private void updateApp(App app) {
        appService.updateById(app);
    }

    private void updateTask(
            Long taskId,
            AppTaskStatus status,
            AppTaskStep step,
            String resultSummary,
            String errorMessage,
            LocalDateTime finishedAt
    ) {
        AppTask task = AppTask.builder()
                .id(taskId)
                .status(status.name())
                .currentStep(step.name())
                .resultSummary(resultSummary)
                .errorMessage(errorMessage)
                .finishedAt(finishedAt)
                .build();
        appTaskService.updateById(task);
    }
}
