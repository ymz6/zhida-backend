package org.ymz.app.service.generation;

import cn.hutool.core.util.StrUtil;
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
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 编排已启动的 ITERATE 任务执行流程。
 *
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppIterationTaskRunner {

    private static final int MAX_REPAIR_ATTEMPTS = 3;

    private final AppService appService;
    private final AppTaskService appTaskService;
    private final AppTaskLogPublisher appTaskLogPublisher;
    private final AppTaskSseBroker appTaskSseBroker;
    private final ProjectWorkspaceManager projectWorkspaceManager;
    private final CodeGenerationAgent codeGenerationAgent;
    private final ProjectCommandRunner projectCommandRunner;
    private final AppTaskMetrics appTaskMetrics;

    public void runIterationTask(Long taskId) {
        AppTask task = appTaskService.getById(taskId);
        if (task == null) {
            return;
        }
        App app = appService.getById(task.getAppId());
        if (app == null) {
            failTask(task, null, null, "应用不存在");
            return;
        }

        String previousPreviewUrl = app.getPreviewUrl();
        try {
            Path workspacePath = workspacePath(app);
            updateApp(App.builder()
                    .id(app.getId())
                    .status(AppStatus.ITERATING.name())
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
                    "迭代任务已启动，Agent 正在基于当前应用继续修改",
                    Map.of("workspacePath", workspacePath.toString())
            );

            if (!Files.isDirectory(workspacePath.resolve("node_modules"))) {
                appTaskLogPublisher.appendMessage(
                        app.getId(),
                        task.getId(),
                        AppChatMessageRole.SYSTEM,
                        AppChatMessageType.CHAT,
                        "工作区依赖缺失，正在安装依赖"
                );
                projectCommandRunner.runPnpmCommand(
                        app.getId(),
                        task.getId(),
                        workspacePath,
                        ProjectCommandRunner.LogMode.SUMMARY,
                        "install",
                        "--frozen-lockfile"
                );
            }

            codeGenerationAgent.iterate(app, task, workspacePath);

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
            LocalDateTime finishedAt = LocalDateTime.now();
            updateTask(task.getId(), AppTaskStatus.SUCCESS, AppTaskStep.FINISHED, "应用迭代成功", null, finishedAt);
            appTaskMetrics.recordCompleted(task, AppTaskStatus.SUCCESS, finishedAt);
            task = appTaskService.getById(task.getId());
            appTaskLogPublisher.publishState(task);
            appTaskLogPublisher.appendMessage(
                    app.getId(),
                    task.getId(),
                    AppChatMessageRole.SYSTEM,
                    AppChatMessageType.CHAT,
                    "应用迭代完成",
                    Map.of("previewUrl", previewUrl)
            );
        } catch (Exception e) {
            log.error("Iterate app task failed, taskId={}", taskId, e);
            failTask(task, app, previousPreviewUrl, e.getMessage());
        } finally {
            appTaskSseBroker.complete(taskId);
        }
    }

    private Path workspacePath(App app) {
        if (StrUtil.isBlank(app.getWorkspacePath())) {
            throw new IllegalStateException("应用工作区不存在，无法继续迭代");
        }
        Path workspacePath = Path.of(app.getWorkspacePath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(workspacePath)) {
            throw new IllegalStateException("应用工作区不存在，无法继续迭代：" + workspacePath);
        }
        return workspacePath;
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

    private void failTask(AppTask task, App app, String previousPreviewUrl, String errorMessage) {
        String message = errorMessage == null || errorMessage.isBlank() ? "应用迭代失败" : errorMessage;
        if (app != null) {
            String status = StrUtil.isBlank(previousPreviewUrl) ? AppStatus.FAILED.name() : AppStatus.READY.name();
            updateApp(App.builder()
                    .id(app.getId())
                    .status(status)
                    .errorMessage(message)
                    .build());
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        updateTask(task.getId(), AppTaskStatus.FAILED, AppTaskStep.FINISHED, null, message, finishedAt);
        appTaskMetrics.recordCompleted(task, AppTaskStatus.FAILED, finishedAt);
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
