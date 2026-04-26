package org.ymz.app.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ymz.app.model.dto.task.TaskStatusResponse;
import org.ymz.app.model.dto.task.TaskStreamEvent;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskStep;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.service.AppTaskRuntimeService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.service.generation.AppCreateTaskRunner;
import org.ymz.app.service.generation.AppTaskLogPublisher;
import org.ymz.app.service.generation.AppTaskSseBroker;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.ymz.app.model.entity.table.AppTaskTableDef.APP_TASK;

/**
 * 启动应用任务，并提供可回放的任务事件流。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppTaskRuntimeServiceImpl implements AppTaskRuntimeService {

    private final AppTaskService appTaskService;
    private final AppTaskLogPublisher appTaskLogPublisher;
    private final AppTaskSseBroker appTaskSseBroker;
    private final AppCreateTaskRunner appCreateTaskRunner;
    @Qualifier("appTaskExecutor")
    private final Executor appTaskExecutor;

    @Override
    public SseEmitter streamTask(Long userId, Long taskId) {
        AppTask task = getOwnedTask(userId, taskId);
        SseEmitter emitter = appTaskSseBroker.createEmitter(taskId);

        appTaskSseBroker.send(emitter, "connected", TaskStreamEvent.state(task));
        List<AppChatMessage> messages = appTaskLogPublisher.listTaskMessages(taskId);
        for (AppChatMessage message : messages) {
            appTaskSseBroker.send(emitter, "message", TaskStreamEvent.message(message));
        }
        appTaskSseBroker.send(emitter, "state", TaskStreamEvent.state(task));

        if (isTerminal(task.getStatus())) {
            emitter.complete();
        }
        return emitter;
    }

    @Override
    public TaskStatusResponse startTask(Long userId, Long taskId) {
        AppTask task = getOwnedTask(userId, taskId);
        if (!AppTaskStatus.PENDING.name().equals(task.getStatus())) {
            return TaskStatusResponse.of(task);
        }
        if (!AppTaskType.CREATE.name().equals(task.getTaskType())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前任务类型暂不支持启动");
        }

        boolean started = appTaskService.updateChain()
                .set(APP_TASK.STATUS, AppTaskStatus.RUNNING.name())
                .set(APP_TASK.CURRENT_STEP, AppTaskStep.INITIALIZING_WORKSPACE.name())
                .set(APP_TASK.STARTED_AT, LocalDateTime.now())
                .where(APP_TASK.ID.eq(taskId))
                .and(APP_TASK.USER_ID.eq(userId))
                .and(APP_TASK.STATUS.eq(AppTaskStatus.PENDING.name()))
                .update();

        AppTask latestTask = appTaskService.getById(taskId);
        if (started) {
            appTaskLogPublisher.publishState(latestTask);
            appTaskExecutor.execute(() -> appCreateTaskRunner.runCreateTask(taskId));
        }
        return TaskStatusResponse.of(latestTask);
    }

    private AppTask getOwnedTask(Long userId, Long taskId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_TASK.ALL_COLUMNS)
                .from(APP_TASK)
                .where(APP_TASK.ID.eq(taskId));
        AppTask task = appTaskService.getOne(query);
        if (task == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "任务不存在");
        }
        if (!userId.equals(task.getUserId())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        return task;
    }

    private boolean isTerminal(String status) {
        return AppTaskStatus.SUCCESS.name().equals(status)
                || AppTaskStatus.FAILED.name().equals(status)
                || AppTaskStatus.CANCELED.name().equals(status);
    }
}
