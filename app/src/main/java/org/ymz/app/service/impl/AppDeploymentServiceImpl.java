package org.ymz.app.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.config.AppDeploymentProperties;
import org.ymz.app.model.dto.app.DeployAppResponse;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.enums.app.AppDeployStatus;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.service.AppDeploymentService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.service.deployment.AppDeploymentFilePublisher;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.ymz.app.model.entity.table.AppTableDef.APP;
import static org.ymz.app.model.entity.table.AppTaskTableDef.APP_TASK;

/**
 * 应用一键部署服务实现。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppDeploymentServiceImpl implements AppDeploymentService {

    private static final int DEPLOY_KEY_MAX_ATTEMPTS = 5;

    private final AppService appService;
    private final AppTaskService appTaskService;
    private final AppDeploymentFilePublisher appDeploymentFilePublisher;
    private final AppDeploymentProperties appDeploymentProperties;

    @Override
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

        try {
            Path distPath = resolveDistPath(app);
            String deployKey = StrUtil.isBlank(app.getDeployKey()) ? generateUniqueDeployKey() : app.getDeployKey();
            Path deployPath = appDeploymentFilePublisher.publish(distPath, deployKey);
            String deployUrl = buildDeployUrl(deployKey);
            LocalDateTime deployedAt = LocalDateTime.now();

            appService.updateById(App.builder()
                    .id(appId)
                    .deployStatus(AppDeployStatus.DEPLOYED.name())
                    .deployKey(deployKey)
                    .deployUrl(deployUrl)
                    .deployPath(deployPath.toString())
                    .deployedAt(deployedAt)
                    .deployErrorMessage("")
                    .build());

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
            if (e instanceof IllegalStateException) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, message);
            }
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, message);
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
