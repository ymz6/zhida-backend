package org.ymz.app.service;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ymz.app.model.dto.task.TaskStatusResponse;
import org.ymz.app.model.dto.task.TaskStreamEvent;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTaskEvent;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskStep;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.ai.codegen.workflow.CodeGenerationTaskRunner;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskEventRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationMessageRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskSseBroker;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
public class AppTaskRuntimeService {

    private final AppTaskService appTaskService;
    private final CodeGenerationMessageRecorder messageRecorder;
    private final CodeGenerationTaskEventRecorder taskEventRecorder;
    private final CodeGenerationTaskSseBroker taskSseBroker;
    private final CodeGenerationTaskRunner codeGenerationTaskRunner;
    private final AppTaskMetrics appTaskMetrics;
    @Qualifier("appTaskExecutor")
    private final Executor appTaskExecutor;

    public SseEmitter streamTask(Long userId, Long taskId) {
        AppTask task = getOwnedTask(userId, taskId);
        SseEmitter emitter = taskSseBroker.createEmitter(taskId);

        taskSseBroker.send(emitter, "connected", TaskStreamEvent.state(task));
        List<AppChatMessage> messages = messageRecorder.listTaskMessages(taskId);
        List<AppTaskEvent> taskEvents = taskEventRecorder.listTaskEvents(taskId);
        replayHistory(emitter, messages, taskEvents);
        taskSseBroker.send(emitter, "state", TaskStreamEvent.state(task));

        if (isTerminal(task.getStatus())) {
            emitter.complete();
        }
        return emitter;
    }

    public TaskStatusResponse startTask(Long userId, Long taskId) {
        AppTask task = getOwnedTask(userId, taskId);
        if (!AppTaskStatus.PENDING.name().equals(task.getStatus())) {
            return TaskStatusResponse.of(task);
        }
        AppTaskType taskType = resolveTaskType(task);
        if (taskType != AppTaskType.CREATE && taskType != AppTaskType.ITERATE) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前任务类型暂不支持启动");
        }
        AppTaskStep initialStep = taskType == AppTaskType.CREATE
                ? AppTaskStep.INITIALIZING_WORKSPACE
                : AppTaskStep.GENERATING_CODE;

        boolean started = appTaskService.updateChain()
                .set(APP_TASK.STATUS, AppTaskStatus.RUNNING.name())
                .set(APP_TASK.CURRENT_STEP, initialStep.name())
                .set(APP_TASK.STARTED_AT, LocalDateTime.now())
                .where(APP_TASK.ID.eq(taskId))
                .and(APP_TASK.USER_ID.eq(userId))
                .and(APP_TASK.STATUS.eq(AppTaskStatus.PENDING.name()))
                .update();

        AppTask latestTask = appTaskService.getById(taskId);
        if (started) {
            appTaskMetrics.recordStarted(latestTask);
            taskEventRecorder.publishStageChanged(latestTask);
            appTaskExecutor.execute(() -> codeGenerationTaskRunner.runTask(taskId));
        }
        return TaskStatusResponse.of(latestTask);
    }

    private AppTaskType resolveTaskType(AppTask task) {
        try {
            return AppTaskType.valueOf(task.getTaskType());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "未知任务类型");
        }
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

    private void replayHistory(SseEmitter emitter, List<AppChatMessage> messages, List<AppTaskEvent> taskEvents) {
        List<HistoryReplayItem> items = new ArrayList<>(messages.size() + taskEvents.size());
        for (AppChatMessage message : messages) {
            String eventName = AppChatMessageRole.ASSISTANT.name().equals(message.getRole())
                    ? "assistant.completed"
                    : "message";
            items.add(new HistoryReplayItem(message.getCreatedAt(), eventName, TaskStreamEvent.message(message)));
        }
        for (AppTaskEvent taskEvent : taskEvents) {
            items.add(new HistoryReplayItem(taskEvent.getCreatedAt(), taskEvent.getEventType(),
                    TaskStreamEvent.taskEvent(taskEvent)));
        }
        items.stream()
                .sorted(Comparator.comparing(HistoryReplayItem::createdAt))
                .forEach(item -> taskSseBroker.send(emitter, item.eventName(), item.payload()));
    }

    private record HistoryReplayItem(LocalDateTime createdAt, String eventName, TaskStreamEvent payload) {
    }
}
