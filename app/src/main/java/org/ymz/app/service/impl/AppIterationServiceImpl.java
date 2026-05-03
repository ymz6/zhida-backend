package org.ymz.app.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ymz.app.model.dto.app.CreateAppIterationRequest;
import org.ymz.app.model.dto.app.CreateAppTaskResponse;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.service.AppIterationService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.service.generation.AppTaskLogPublisher;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.ymz.app.model.entity.table.AppTaskTableDef.APP_TASK;

/**
 * 创建应用后续对话迭代任务。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppIterationServiceImpl implements AppIterationService {

    private final AppService appService;
    private final AppTaskService appTaskService;
    private final AppTaskLogPublisher appTaskLogPublisher;
    private final AppTaskMetrics appTaskMetrics;

    @Override
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

        appTaskLogPublisher.appendMessage(
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
}
