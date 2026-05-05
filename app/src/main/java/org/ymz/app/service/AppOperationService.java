package org.ymz.app.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ymz.app.ai.codegen.event.CodeGenerationMessageRecorder;
import org.ymz.app.ai.title.TitleGenerateAssistant;
import org.ymz.app.ai.title.TitleGenerateResult;
import org.ymz.app.config.AppDeploymentProperties;
import org.ymz.app.deployment.AppCoverCaptureService;
import org.ymz.app.deployment.AppDeploymentFileService;
import org.ymz.app.model.dto.app.CreateAppIterationRequest;
import org.ymz.app.model.dto.app.CreateAppRequest;
import org.ymz.app.model.dto.app.CreateAppTaskResponse;
import org.ymz.app.model.dto.app.DeployAppResponse;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;
import org.ymz.app.model.enums.app.AppDeployStatus;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskStep;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.ymz.app.model.entity.table.AppTableDef.APP;
import static org.ymz.app.model.entity.table.AppTaskTableDef.APP_TASK;

/**
 * 应用创建、迭代和部署业务。
 *
 * @author ymz
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppOperationService {

    private static final int DEPLOY_KEY_MAX_ATTEMPTS = 5;

    private final TitleGenerateAssistant titleGenerateAssistant;
    private final AppService appService;
    private final AppTaskService appTaskService;
    private final CodeGenerationMessageRecorder messageRecorder;
    private final AppDeploymentFileService appDeploymentFileService;
    private final AppDeploymentProperties appDeploymentProperties;
    private final AppCoverCaptureService appCoverCaptureService;
    private final AppTaskMetrics appTaskMetrics;

    @Transactional
    public CreateAppTaskResponse createApp(Long userId, CreateAppRequest request) {
        String prompt = StrUtil.trim(request.getPrompt());
        TitleGenerateResult titleResult = titleGenerateAssistant.chat(prompt);
        if (titleResult == null || !titleResult.isAccepted() || StrUtil.isBlank(titleResult.getTitle())) {
            String reason = titleResult == null || titleResult.getReason() == null
                    ? "无法识别应用需求"
                    : titleResult.getReason().getDescription();
            throw BusinessException.of(ResultCode.INVALID_PARAM, reason);
        }

        LocalDateTime now = LocalDateTime.now();
        App app = App.builder()
                .userId(userId)
                .name(titleResult.getTitle())
                .initPrompt(prompt)
                .status(AppStatus.CREATING.name())
                .deployStatus(AppDeployStatus.UNDEPLOYED.name())
                .createdAt(now)
                .build();
        appService.save(app);

        AppTask task = AppTask.builder()
                .appId(app.getId())
                .userId(userId)
                .taskType(AppTaskType.CREATE.name())
                .prompt(prompt)
                .status(AppTaskStatus.PENDING.name())
                .createdAt(now)
                .build();
        appTaskService.save(task);
        appTaskMetrics.recordCreated(AppTaskType.CREATE);

        app.setLatestTaskId(task.getId());
        appService.updateById(app);

        messageRecorder.appendMessage(
                app.getId(),
                task.getId(),
                AppChatMessageRole.USER,
                AppChatMessageType.CHAT,
                prompt,
                Map.of(
                        "title", titleResult.getTitle(),
                        "reason", titleResult.getReason() == null ? "OK" : titleResult.getReason().name()
                )
        );

        return CreateAppTaskResponse.builder()
                .appId(app.getId())
                .taskId(task.getId())
                .name(app.getName())
                .status(app.getStatus())
                .build();
    }

    @Transactional
    public CreateAppTaskResponse createAppIteration(Long userId, Long appId, CreateAppIterationRequest request) {
        String prompt = StrUtil.trim(request.getPrompt());
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!userId.equals(app.getUserId())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        if (!canIterate(app.getStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用状态不支持继续迭代");
        }
        if (StrUtil.isBlank(app.getWorkspacePath()) || !Files.isDirectory(Path.of(app.getWorkspacePath()))) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "应用工作区不存在，无法继续迭代");
        }
        if (hasActiveTask(appId)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用已有任务正在执行");
        }

        LocalDateTime now = LocalDateTime.now();
        AppTask task = AppTask.builder()
                .appId(appId)
                .userId(userId)
                .taskType(AppTaskType.ITERATE.name())
                .prompt(prompt)
                .status(AppTaskStatus.PENDING.name())
                .createdAt(now)
                .build();
        appTaskService.save(task);
        appTaskMetrics.recordCreated(AppTaskType.ITERATE);

        app.setLatestTaskId(task.getId());
        appService.updateById(app);

        messageRecorder.appendMessage(
                appId,
                task.getId(),
                AppChatMessageRole.USER,
                AppChatMessageType.CHAT,
                prompt
        );

        return CreateAppTaskResponse.builder()
                .appId(appId)
                .taskId(task.getId())
                .name(app.getName())
                .status(app.getStatus())
                .build();
    }

    public DeployAppResponse deployApp(Long userId, Long appId) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!userId.equals(app.getUserId())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        if (!AppStatus.READY.name().equals(app.getStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用状态不支持部署");
        }
        if (hasActiveGenerationTask(appId)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用已有任务正在执行");
        }

        boolean locked = appService.updateChain()
                .set(APP.DEPLOY_STATUS, AppDeployStatus.DEPLOYING.name())
                .set(APP.DEPLOY_ERROR_MESSAGE, "")
                .where(APP.ID.eq(appId))
                .and(APP.USER_ID.eq(userId))
                .and(APP.STATUS.eq(AppStatus.READY.name()))
                .and(APP.DEPLOY_STATUS.ne(AppDeployStatus.DEPLOYING.name()))
                .update();
        if (!locked) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用正在部署，请稍后再试");
        }

        LocalDateTime startedAt = LocalDateTime.now();
        AppTask deployTask = AppTask.builder()
                .appId(appId)
                .userId(userId)
                .taskType(AppTaskType.DEPLOY.name())
                .prompt("部署应用")
                .status(AppTaskStatus.RUNNING.name())
                .currentStep(AppTaskStep.DEPLOYING.name())
                .createdAt(startedAt)
                .startedAt(startedAt)
                .build();
        appTaskService.save(deployTask);
        appTaskMetrics.recordCreated(AppTaskType.DEPLOY);
        appTaskMetrics.recordStarted(deployTask);
        appService.updateById(App.builder()
                .id(appId)
                .latestTaskId(deployTask.getId())
                .build());

        try {
            Path distPath = resolveDistPath(app);
            String deployKey = StrUtil.isBlank(app.getDeployKey()) ? generateUniqueDeployKey() : app.getDeployKey();
            Path deployPath = appDeploymentFileService.deployDist(distPath, deployKey);
            String deployUrl = buildDeployUrl(deployKey);
            LocalDateTime taskFinishedAt = LocalDateTime.now();
            LocalDateTime deployedAt = taskFinishedAt.withNano(0);

            appService.updateById(App.builder()
                    .id(appId)
                    .deployStatus(AppDeployStatus.DEPLOYED.name())
                    .deployKey(deployKey)
                    .deployUrl(deployUrl)
                    .deployPath(deployPath.toString())
                    .deployedAt(deployedAt)
                    .deployErrorMessage("")
                    .build());

            triggerCoverCapture(appId, deployUrl, deployedAt);
            completeDeployTask(deployTask, AppTaskStatus.SUCCESS, "应用部署成功", null, taskFinishedAt);

            return DeployAppResponse.builder()
                    .appId(appId)
                    .deployStatus(AppDeployStatus.DEPLOYED.name())
                    .deployUrl(deployUrl)
                    .deployedAt(deployedAt)
                    .build();
        } catch (Exception e) {
            String message = StrUtil.blankToDefault(e.getMessage(), "应用部署失败");
            appService.updateById(App.builder()
                    .id(appId)
                    .deployStatus(AppDeployStatus.FAILED.name())
                    .deployErrorMessage(message)
                    .build());
            completeDeployTask(deployTask, AppTaskStatus.FAILED, null, message, LocalDateTime.now());
            if (e instanceof IllegalStateException) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, message);
            }
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, message);
        }
    }

    private boolean canIterate(String status) {
        return AppStatus.READY.name().equals(status) || AppStatus.FAILED.name().equals(status);
    }

    private boolean hasActiveTask(Long appId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_TASK.ID)
                .from(APP_TASK)
                .where(APP_TASK.APP_ID.eq(appId))
                .and(APP_TASK.STATUS.in(List.of(
                        AppTaskStatus.PENDING.name(),
                        AppTaskStatus.RUNNING.name()
                )));
        return appTaskService.count(query) > 0;
    }

    private void completeDeployTask(
            AppTask deployTask,
            AppTaskStatus status,
            String resultSummary,
            String errorMessage,
            LocalDateTime finishedAt
    ) {
        appTaskService.updateById(AppTask.builder()
                .id(deployTask.getId())
                .status(status.name())
                .currentStep(AppTaskStep.FINISHED.name())
                .resultSummary(resultSummary)
                .errorMessage(errorMessage)
                .finishedAt(finishedAt)
                .build());
        appTaskMetrics.recordCompleted(deployTask, status, finishedAt);
    }

    private void triggerCoverCapture(Long appId, String deployUrl, LocalDateTime deployedAt) {
        try {
            appCoverCaptureService.captureCoverAsync(appId, deployUrl, deployedAt);
        } catch (Exception e) {
            log.warn("提交应用封面生成任务失败: appId={}, deployUrl={}", appId, deployUrl, e);
        }
    }

    private Path resolveDistPath(App app) {
        if (StrUtil.isBlank(app.getWorkspacePath())) {
            throw new IllegalStateException("应用工作区不存在，无法部署");
        }
        Path workspacePath = Path.of(app.getWorkspacePath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(workspacePath)) {
            throw new IllegalStateException("应用工作区不存在，无法部署：" + workspacePath);
        }
        Path distPath = workspacePath.resolve("dist").normalize();
        if (!Files.isDirectory(distPath) || !Files.isRegularFile(distPath.resolve("index.html"))) {
            throw new IllegalStateException("构建产物不存在，请检查 dist/index.html");
        }
        return distPath;
    }

    private String generateUniqueDeployKey() {
        for (int i = 0; i < DEPLOY_KEY_MAX_ATTEMPTS; i++) {
            String deployKey = IdUtil.simpleUUID();
            QueryWrapper query = QueryWrapper.create()
                    .select(APP.ID)
                    .from(APP)
                    .where(APP.DEPLOY_KEY.eq(deployKey));
            if (appService.count(query) == 0) {
                return deployKey;
            }
        }
        throw new IllegalStateException("部署标识生成失败，请重试");
    }

    private String buildDeployUrl(String deployKey) {
        String prefix = StrUtil.blankToDefault(appDeploymentProperties.getDeployUrlPrefix(), "http://localhost/apps");
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + deployKey + "/";
    }

    private boolean hasActiveGenerationTask(Long appId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_TASK.ID)
                .from(APP_TASK)
                .where(APP_TASK.APP_ID.eq(appId))
                .and(APP_TASK.TASK_TYPE.in(List.of(
                        AppTaskType.CREATE.name(),
                        AppTaskType.ITERATE.name()
                )))
                .and(APP_TASK.STATUS.in(List.of(
                        AppTaskStatus.PENDING.name(),
                        AppTaskStatus.RUNNING.name()
                )));
        return appTaskService.count(query) > 0;
    }
}
