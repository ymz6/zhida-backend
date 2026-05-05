package org.ymz.app.ai.codegen.event;

import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.codegen.runtime.CodeGenerationContext;
import org.ymz.app.model.dto.task.TaskStreamEvent;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.entity.AppTaskEvent;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;
import org.ymz.app.model.enums.app.AppTaskEventType;
import org.ymz.app.service.AppTaskEventService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.ymz.app.model.entity.table.AppTaskEventTableDef.APP_TASK_EVENT;

/**
 * 新版 Agent 运行事件发布器。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class CodeGenerationTaskEventRecorder {

    private static final int PREVIEW_LIMIT = 3_000;

    private final CodeGenerationMessageRecorder messageRecorder;
    private final AppTaskEventService appTaskEventService;
    private final CodeGenerationTaskSseBroker taskSseBroker;
    private final ObjectMapper objectMapper;
    public void publishTextDelta(CodeGenerationContext context, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        taskSseBroker.publish(
                context.getTaskId(),
                AppTaskEventType.ASSISTANT_COMPLETED.getCode().replace(".completed", ".delta"),
                TaskStreamEvent.assistantDelta(context.getAppId(), context.getTaskId(), delta)
        );
    }
    public AppChatMessage appendAssistantMessage(CodeGenerationContext context, String content) {
        AppChatMessage message = messageRecorder.appendMessage(
                context.getAppId(),
                context.getTaskId(),
                AppChatMessageRole.ASSISTANT,
                AppChatMessageType.CHAT,
                content
        );
        taskSseBroker.publish(
                context.getTaskId(),
                AppTaskEventType.ASSISTANT_COMPLETED.getCode(),
                TaskStreamEvent.message(message)
        );
        return message;
    }
    public void publishRunStarted(CodeGenerationContext context) {
        publishEvent(
                context.getAppId(),
                context.getTaskId(),
                AppTaskEventType.AGENT_RUN_STARTED,
                "Agent 开始执行 " + context.getScenario().name() + " 场景",
                Map.of(
                        "scenario", context.getScenario().name(),
                        "appName", context.getAppName()
                )
        );
    }
    public void publishRunFinished(CodeGenerationContext context, String summary) {
        publishEvent(
                context.getAppId(),
                context.getTaskId(),
                AppTaskEventType.AGENT_RUN_FINISHED,
                preview(summary),
                Map.of("scenario", context.getScenario().name())
        );
    }
    public void publishRunFailed(CodeGenerationContext context, String errorMessage) {
        publishEvent(
                context.getAppId(),
                context.getTaskId(),
                AppTaskEventType.AGENT_RUN_FAILED,
                preview(errorMessage),
                Map.of("scenario", context.getScenario().name())
        );
    }
    public void publishStageChanged(AppTask task) {
        messageRecorder.publishState(task);
        publishEvent(
                task.getAppId(),
                task.getId(),
                AppTaskEventType.AGENT_STAGE_CHANGED,
                "任务阶段切换为 " + task.getCurrentStep(),
                Map.of(
                        "status", task.getStatus(),
                        "currentStep", task.getCurrentStep()
                )
        );
    }
    public void publishCommandStarted(Long appId, Long taskId, String commandText) {
        publishEvent(
                appId,
                taskId,
                AppTaskEventType.AGENT_COMMAND_STARTED,
                "开始执行命令: " + commandText,
                Map.of("command", commandText)
        );
    }
    public void publishCommandFinished(Long appId, Long taskId, CodeGenerationCommandResult result) {
        AppTaskEventType eventType = result.isSuccess()
                ? AppTaskEventType.AGENT_COMMAND_SUCCEEDED
                : AppTaskEventType.AGENT_COMMAND_FAILED;
        publishEvent(
                appId,
                taskId,
                eventType,
                preview(result.getContent()),
                Map.of(
                        "command", result.getCommandText(),
                        "exitCode", result.getExitCode(),
                        "success", result.isSuccess(),
                        "durationMillis", result.getDurationMillis(),
                        "timedOut", result.isTimedOut()
                )
        );
    }
    public void publishToolCalled(CodeGenerationContext context, ToolExecutionRequest request) {
        publishEvent(
                context.getAppId(),
                context.getTaskId(),
                AppTaskEventType.AGENT_TOOL_CALLED,
                summarizeToolCall(request),
                Map.of("toolName", request.name())
        );
    }
    public void publishToolExecuted(CodeGenerationContext context, ToolExecution execution) {
        AppTaskEventType eventType = execution.hasFailed()
                ? AppTaskEventType.AGENT_TOOL_FAILED
                : AppTaskEventType.AGENT_TOOL_SUCCEEDED;
        publishEvent(
                context.getAppId(),
                context.getTaskId(),
                eventType,
                preview(execution.result()),
                Map.of(
                        "toolName", execution.request().name(),
                        "hasFailed", execution.hasFailed()
                )
        );
    }
    public void publishValidationStarted(Long appId, Long taskId) {
        publishEvent(
                appId,
                taskId,
                AppTaskEventType.AGENT_VALIDATION_STARTED,
                "开始执行 pnpm lint / pnpm build 校验",
                Map.of()
        );
    }
    public void publishValidationFailed(Long appId, Long taskId, CodeGenerationCommandResult failedCommand) {
        publishEvent(
                appId,
                taskId,
                AppTaskEventType.AGENT_VALIDATION_FAILED,
                preview(failedCommand == null ? "校验失败" : failedCommand.getContent()),
                Map.of(
                        "command", failedCommand == null ? null : failedCommand.getCommandText(),
                        "exitCode", failedCommand == null ? null : failedCommand.getExitCode()
                )
        );
    }
    public void publishRepairStarted(Long appId, Long taskId, int repairAttempt, CodeGenerationCommandResult failedCommand) {
        publishEvent(
                appId,
                taskId,
                AppTaskEventType.AGENT_REPAIR_STARTED,
                "开始第 " + repairAttempt + " 轮自动修复",
                Map.of(
                        "repairAttempt", repairAttempt,
                        "command", failedCommand == null ? null : failedCommand.getCommandText()
                )
        );
    }
    public List<AppTaskEvent> listTaskEvents(Long taskId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_TASK_EVENT.ALL_COLUMNS)
                .from(APP_TASK_EVENT)
                .where(APP_TASK_EVENT.TASK_ID.eq(taskId))
                .orderBy(APP_TASK_EVENT.CREATED_AT.asc())
                .orderBy(APP_TASK_EVENT.ID.asc());
        return appTaskEventService.list(query);
    }

    private void publishEvent(
            Long appId,
            Long taskId,
            AppTaskEventType eventType,
            String content,
            Map<String, Object> metadata
    ) {
        AppTaskEvent event = AppTaskEvent.builder()
                .appId(appId)
                .taskId(taskId)
                .eventType(eventType.getCode())
                .content(content == null ? "" : content)
                .metadata(toJson(metadata))
                .createdAt(LocalDateTime.now())
                .build();
        appTaskEventService.save(event);
        taskSseBroker.publish(taskId, eventType.getCode(), TaskStreamEvent.taskEvent(event));
    }

    private String summarizeToolCall(ToolExecutionRequest request) {
        String arguments = request.arguments();
        if (arguments == null || arguments.isBlank()) {
            return "调用工具：" + request.name();
        }
        return "调用工具：" + request.name() + " " + preview(arguments);
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= PREVIEW_LIMIT) {
            return content;
        }
        return content.substring(0, PREVIEW_LIMIT) + "\n...[truncated]";
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{\"metadataError\":\"failed to serialize metadata\"}";
        }
    }
}
