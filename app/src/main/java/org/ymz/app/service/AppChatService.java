package org.ymz.app.service;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ymz.app.ai.codegen.event.CodeGenerationMessageRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskEventRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskSseBroker;
import org.ymz.app.ai.codegen.workflow.CodeGenerationTaskRunner;
import org.ymz.app.model.dto.app.AppStreamEvent;
import org.ymz.app.model.dto.app.ChatRequest;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.entity.AppTaskEvent;
import org.ymz.app.model.enums.app.*;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;

import static org.ymz.app.model.entity.table.AppTaskTableDef.APP_TASK;

/**
 * 应用对话入口：CODE 模式触发代码生成、CHAT 模式只读问答、RESUME 模式重连当前任务流。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppChatService {

    private final AppService appService;
    private final AppTaskService appTaskService;
    private final CodeGenerationMessageRecorder messageRecorder;
    private final CodeGenerationTaskEventRecorder taskEventRecorder;
    private final CodeGenerationTaskSseBroker taskSseBroker;
    private final CodeGenerationTaskRunner codeGenerationTaskRunner;
    private final AppTaskMetrics appTaskMetrics;
    private final Executor appTaskExecutor;

    @Transactional
    public SseEmitter chat(Long userId, Long appId, ChatRequest request) {
        if (request.getMode() == AppChatMode.RESUME) {
            return resume(userId, appId);
        }
        String prompt = StrUtil.trim(request.getPrompt());
        if (StrUtil.isBlank(prompt)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "请输入对话内容");
        }
        AppTask task = createChatTask(userId, appId, request.getMode(), prompt);

        SseEmitter emitter = taskSseBroker.createEmitter(task.getId());
        taskSseBroker.send(emitter, "connected", AppStreamEvent.state(task));
        taskSseBroker.send(emitter, "state", AppStreamEvent.state(task));
        Long taskId = task.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                appTaskExecutor.execute(() -> codeGenerationTaskRunner.runTask(taskId));
            }
        });
        return emitter;
    }

    private SseEmitter resume(Long userId, Long appId) {
        App app = getOwnedApp(userId, appId);
        Long latestTaskId = app.getLatestTaskId();
        if (latestTaskId == null) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "应用尚未开始对话");
        }
        AppTask task = appTaskService.getById(latestTaskId);
        if (task == null) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "应用尚未开始对话");
        }

        SseEmitter emitter = taskSseBroker.createEmitter(task.getId());
        taskSseBroker.send(emitter, "connected", AppStreamEvent.state(task));
        replayHistory(emitter, messageRecorder.listTaskMessages(task.getId()),
                taskEventRecorder.listTaskEvents(task.getId()));
        taskSseBroker.send(emitter, "state", AppStreamEvent.state(task));
        if (isTerminal(task.getStatus())) {
            emitter.complete();
        }
        return emitter;
    }

    private AppTask createChatTask(Long userId, Long appId, AppChatMode mode, String prompt) {
        App app = getOwnedApp(userId, appId);
        AppTaskType taskType = resolveTaskType(app, mode);
        AppTaskStep initialStep = initialStep(taskType);

        validateState(app, taskType);
        if (hasActiveGenerationTask(appId)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用已有任务正在执行");
        }

        LocalDateTime now = LocalDateTime.now();
        AppTask task = AppTask.builder()
                .appId(appId)
                .userId(userId)
                .taskType(taskType.name())
                .prompt(prompt)
                .status(AppTaskStatus.RUNNING.name())
                .currentStep(initialStep.name())
                .createdAt(now)
                .startedAt(now)
                .build();
        appTaskService.save(task);
        appTaskMetrics.recordCreated(taskType);
        appTaskMetrics.recordStarted(task);

        appService.updateById(App.builder()
                .id(appId)
                .latestTaskId(task.getId())
                .build());

        messageRecorder.appendMessage(
                appId,
                task.getId(),
                AppChatMessageRole.USER,
                prompt
        );
        return task;
    }

    private AppTaskType resolveTaskType(App app, AppChatMode mode) {
        if (mode == AppChatMode.CHAT) {
            return AppTaskType.CHAT;
        }
        // 如果已有工作区，则认为是迭代任务，反之为生成任务
        return StrUtil.isBlank(app.getWorkspacePath()) ? AppTaskType.CREATE : AppTaskType.ITERATE;
    }

    private AppTaskStep initialStep(AppTaskType taskType) {
        return switch (taskType) {
            case CREATE -> AppTaskStep.INITIALIZING_WORKSPACE;
            case ITERATE -> AppTaskStep.GENERATING_CODE;
            case CHAT -> AppTaskStep.CHATTING;
            case DEPLOY -> AppTaskStep.DEPLOYING;
        };
    }

    private void validateState(App app, AppTaskType taskType) {
        switch (taskType) {
            case CREATE -> {
                if (!AppStatus.CREATING.name().equals(app.getStatus())) {
                    throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用状态不支持启动对话");
                }
            }
            case ITERATE -> {
                if (!canIterate(app.getStatus())) {
                    throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用状态不支持继续对话");
                }
                if (!Files.isDirectory(Path.of(app.getWorkspacePath()))) {
                    throw BusinessException.of(ResultCode.INVALID_PARAM, "应用工作区不存在，无法继续对话");
                }
            }
            case CHAT -> {
                if (AppStatus.CREATING.name().equals(app.getStatus())) {
                    throw BusinessException.of(ResultCode.INVALID_PARAM, "应用尚未生成，无法对话答疑");
                }
            }
            default -> throw BusinessException.of(ResultCode.INVALID_PARAM, "不支持的对话任务类型");
        }
    }

    private void replayHistory(SseEmitter emitter, List<AppChatMessage> messages, List<AppTaskEvent> taskEvents) {
        List<HistoryReplayItem> items = new ArrayList<>(messages.size() + taskEvents.size());
        for (AppChatMessage message : messages) {
            String eventName = AppChatMessageRole.ASSISTANT.name().equals(message.getRole())
                    ? "assistant.completed"
                    : "message";
            items.add(new HistoryReplayItem(message.getCreatedAt(), eventName, AppStreamEvent.message(message, eventName)));
        }
        for (AppTaskEvent taskEvent : taskEvents) {
            items.add(new HistoryReplayItem(taskEvent.getCreatedAt(), taskEvent.getEventType(),
                    AppStreamEvent.taskEvent(taskEvent)));
        }
        items.stream()
                .sorted(Comparator.comparing(HistoryReplayItem::createdAt))
                .forEach(item -> taskSseBroker.send(emitter, item.eventName(), item.payload()));
    }

    private App getOwnedApp(Long userId, Long appId) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!userId.equals(app.getUserId())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        return app;
    }

    private boolean canIterate(String status) {
        return AppStatus.READY.name().equals(status) || AppStatus.FAILED.name().equals(status);
    }

    private boolean hasActiveGenerationTask(Long appId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_TASK.ID)
                .from(APP_TASK)
                .where(APP_TASK.APP_ID.eq(appId))
                .and(APP_TASK.TASK_TYPE.in(List.of(
                        AppTaskType.CREATE.name(),
                        AppTaskType.ITERATE.name(),
                        AppTaskType.CHAT.name()
                )))
                .and(APP_TASK.STATUS.in(List.of(
                        AppTaskStatus.PENDING.name(),
                        AppTaskStatus.RUNNING.name()
                )));
        return appTaskService.count(query) > 0;
    }

    private boolean isTerminal(String status) {
        return AppTaskStatus.SUCCESS.name().equals(status)
                || AppTaskStatus.FAILED.name().equals(status);
    }

    private record HistoryReplayItem(LocalDateTime createdAt, String eventName, AppStreamEvent payload) {
    }
}
