package org.ymz.app.ai.codegen.workflow;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.codegen.event.CodeGenerationMessageRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskEventRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskSseBroker;
import org.ymz.app.ai.codegen.memory.AppContextSummaryManager;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandRunner;
import org.ymz.app.ai.codegen.workspace.CodeGenerationProjectVerifier;
import org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceManager;
import org.ymz.app.model.dto.app.ChatMessageMetadata;
import org.ymz.app.model.dto.app.content.TextBlock;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageContentType;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskEventType;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskStep;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 编排已启动的应用代码生成任务。
 *
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeGenerationTaskRunner {

    private static final int MAX_REPAIR_ATTEMPTS = 3;

    private final AppService appService;
    private final AppTaskService appTaskService;
    private final CodeGenerationMessageRecorder messageRecorder;
    private final CodeGenerationTaskEventRecorder taskEventRecorder;
    private final CodeGenerationTaskSseBroker taskSseBroker;
    private final CodeGenerationWorkspaceManager workspaceManager;
    private final CodeGenerationAgentExecutor agentExecutor;
    private final AppContextSummaryManager appContextSummaryManager;
    private final CodeGenerationCommandRunner commandRunner;
    private final CodeGenerationProjectVerifier projectVerifier;
    private final AppTaskMetrics appTaskMetrics;

    public void runTask(Long taskId) {
        AppTask task = appTaskService.getById(taskId);
        if (task == null) {
            return;
        }
        App app = appService.getById(task.getAppId());
        if (app == null) {
            failTask(task, null, null, "应用不存在");
            taskSseBroker.complete(taskId);
            return;
        }
        if (AppTaskType.CHAT.name().equals(task.getTaskType())) {
            runChat(app, task);
            return;
        }

        String previousPreviewUrl = app.getPreviewUrl();
        try {
            AppTaskType taskType = resolveTaskType(task);
            Path workspacePath = prepareGeneration(taskType, app, task);
            task = appTaskService.getById(task.getId());
            app = appService.getById(app.getId());

            if (taskType == AppTaskType.CREATE) {
                agentExecutor.generate(app, task, workspacePath);
            } else {
                agentExecutor.iterate(app, task, workspacePath);
            }

            updateApp(App.builder()
                    .id(app.getId())
                    .status(AppStatus.BUILDING.name())
                    .build());
            updateTask(task.getId(), AppTaskStatus.RUNNING, AppTaskStep.BUILDING, null, null, null);
            task = appTaskService.getById(task.getId());
            taskEventRecorder.publishStageChanged(task);

            verifyProject(app, task, workspacePath);
            appContextSummaryManager.refresh(app, task, workspacePath);

            String previewUrl = workspaceManager.refreshPreview(app.getId(), workspacePath);
            updateApp(App.builder()
                    .id(app.getId())
                    .previewUrl(previewUrl)
                    .status(AppStatus.READY.name())
                    .errorMessage("")
                    .build());
            LocalDateTime finishedAt = LocalDateTime.now();
            updateTask(task.getId(), AppTaskStatus.SUCCESS, AppTaskStep.FINISHED, successSummary(taskType), null, finishedAt);
            appTaskMetrics.recordCompleted(task, AppTaskStatus.SUCCESS, finishedAt);
            task = appTaskService.getById(task.getId());
            attachPreviewMetadata(task.getId(), previewUrl);
            taskEventRecorder.publishStageChanged(task);
        } catch (Exception e) {
            log.error("Code generation task failed, taskId={}", taskId, e);
            failTask(task, app, previousPreviewUrl, e.getMessage());
        } finally {
            taskSseBroker.complete(taskId);
        }
    }

    private Path prepareGeneration(AppTaskType taskType, App app, AppTask task) throws Exception {
        if (taskType == AppTaskType.CREATE) {
            Path workspacePath = workspaceManager.initializeWorkspace(app.getId());
            updateApp(App.builder()
                    .id(app.getId())
                    .workspacePath(workspacePath.toString())
                    .status(AppStatus.GENERATING.name())
                    .build());
            updateTask(task.getId(), AppTaskStatus.RUNNING, AppTaskStep.GENERATING_CODE, null, null, null);
            taskEventRecorder.publishStageChanged(appTaskService.getById(task.getId()));
            commandRunner.runPnpmCommand(
                    app.getId(),
                    task.getId(),
                    workspacePath,
                    CodeGenerationCommandRunner.LogMode.SUMMARY,
                    "install",
                    "--frozen-lockfile"
            );
            return workspacePath;
        }

        Path workspacePath = workspacePath(app);
        updateApp(App.builder()
                .id(app.getId())
                .status(AppStatus.ITERATING.name())
                .build());
        updateTask(task.getId(), AppTaskStatus.RUNNING, AppTaskStep.GENERATING_CODE, null, null, null);
        taskEventRecorder.publishStageChanged(appTaskService.getById(task.getId()));

        if (!Files.isDirectory(workspacePath.resolve("node_modules"))) {
            commandRunner.runPnpmCommand(
                    app.getId(),
                    task.getId(),
                    workspacePath,
                    CodeGenerationCommandRunner.LogMode.SUMMARY,
                    "install",
                    "--frozen-lockfile"
            );
        }
        return workspacePath;
    }

    private void verifyProject(App app, AppTask task, Path workspacePath) {
        for (int repairCount = 0; repairCount <= MAX_REPAIR_ATTEMPTS; repairCount++) {
            taskEventRecorder.publishValidationStarted(app.getId(), task.getId());
            CodeGenerationCommandResult failedCommand = projectVerifier.verify(app.getId(), task.getId(), workspacePath);
            if (failedCommand == null) {
                return;
            }
            taskEventRecorder.publishValidationFailed(app.getId(), task.getId(), failedCommand);

            if (repairCount == MAX_REPAIR_ATTEMPTS) {
                throw new IllegalStateException("应用校验失败，已自动修复 "
                        + MAX_REPAIR_ATTEMPTS + " 轮仍未通过：" + failedCommand.getErrorMessage());
            }

            int repairAttempt = repairCount + 1;
            taskEventRecorder.publishRepairStarted(app.getId(), task.getId(), repairAttempt, failedCommand);
            agentExecutor.repair(app, task, workspacePath, failedCommand, repairAttempt);
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

    private AppTaskType resolveTaskType(AppTask task) {
        try {
            AppTaskType taskType = AppTaskType.valueOf(task.getTaskType());
            if (taskType == AppTaskType.CREATE || taskType == AppTaskType.ITERATE) {
                return taskType;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
        }
        throw new IllegalStateException("当前任务类型暂不支持执行：" + task.getTaskType());
    }

    private void runChat(App app, AppTask task) {
        try {
            Path workspacePath = StrUtil.isBlank(app.getWorkspacePath()) ? null : workspacePath(app);
            agentExecutor.chat(app, task, workspacePath);
            LocalDateTime finishedAt = LocalDateTime.now();
            updateTask(task.getId(), AppTaskStatus.SUCCESS, AppTaskStep.FINISHED, "对话完成", null, finishedAt);
            appTaskMetrics.recordCompleted(task, AppTaskStatus.SUCCESS, finishedAt);
            taskEventRecorder.publishStageChanged(appTaskService.getById(task.getId()));
        } catch (Exception e) {
            log.error("Chat task failed, taskId={}", task.getId(), e);
            String message = StrUtil.blankToDefault(e.getMessage(), "对话失败");
            LocalDateTime finishedAt = LocalDateTime.now();
            updateTask(task.getId(), AppTaskStatus.FAILED, AppTaskStep.FINISHED, null, message, finishedAt);
            appTaskMetrics.recordCompleted(task, AppTaskStatus.FAILED, finishedAt);
            attachErrorMetadata(task.getAppId(), task.getId(), AppTaskType.CHAT, message);
            taskEventRecorder.publishStageChanged(appTaskService.getById(task.getId()));
        } finally {
            taskSseBroker.complete(task.getId());
        }
    }

    private void failTask(AppTask task, App app, String previousPreviewUrl, String errorMessage) {
        AppTaskType taskType = safeTaskType(task);
        String message = StrUtil.blankToDefault(errorMessage, failureMessage(taskType));
        if (app != null) {
            String status = taskType == AppTaskType.ITERATE && StrUtil.isNotBlank(previousPreviewUrl)
                    ? AppStatus.READY.name()
                    : AppStatus.FAILED.name();
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
        attachErrorMetadata(task.getAppId(), task.getId(), taskType, message);
        taskEventRecorder.publishStageChanged(latestTask);
    }

    private AppTaskType safeTaskType(AppTask task) {
        try {
            return AppTaskType.valueOf(task.getTaskType());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private String successSummary(AppTaskType taskType) {
        return taskType == AppTaskType.ITERATE ? "应用迭代成功" : "应用生成成功";
    }

    private String failureMessage(AppTaskType taskType) {
        return taskType == AppTaskType.ITERATE ? "应用迭代失败" : "应用生成失败";
    }

    private void attachPreviewMetadata(Long taskId, String previewUrl) {
        AppChatMessage assistantMessage = messageRecorder.getLastAssistantMessage(taskId);
        if (assistantMessage == null) {
            return;
        }
        messageRecorder.updateMetadata(
                assistantMessage.getId(),
                new ChatMessageMetadata(previewUrl, null)
        );
    }

    private void attachErrorMetadata(Long appId, Long taskId, AppTaskType taskType, String detail) {
        ChatMessageMetadata metadata = new ChatMessageMetadata(
                null,
                new ChatMessageMetadata.ErrorInfo(errorType(taskType), detail)
        );
        AppChatMessage assistantMessage = messageRecorder.getLastAssistantMessage(taskId);
        if (assistantMessage != null) {
            messageRecorder.updateMetadata(assistantMessage.getId(), metadata);
            return;
        }

        // 如果模型尚未产出 assistant 消息，补一条用户可见的失败回复承载错误元信息。
        String fallbackText = taskType == AppTaskType.CHAT ? "对话处理失败" : failureMessage(taskType);
        AppChatMessage message = messageRecorder.saveMessage(
                appId,
                taskId,
                AppChatMessageRole.ASSISTANT,
                AppChatMessageContentType.BLOCKS,
                messageRecorder.serializeBlocks(List.of(new TextBlock(fallbackText))),
                metadata
        );
        messageRecorder.publishMessage(taskId, AppTaskEventType.ASSISTANT_COMPLETED.getCode(), message);
    }

    private String errorType(AppTaskType taskType) {
        if (taskType == AppTaskType.CHAT) {
            return "CHAT_FAILED";
        }
        if (taskType == AppTaskType.ITERATE) {
            return "ITERATE_FAILED";
        }
        return "CREATE_FAILED";
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
