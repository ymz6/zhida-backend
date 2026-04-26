package org.ymz.app.service.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.model.dto.task.TaskStreamEvent;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;
import org.ymz.app.service.AppChatMessageService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.ymz.app.model.entity.table.AppChatMessageTableDef.APP_CHAT_MESSAGE;

/**
 * 写入任务日志，并同步推送给已连接的事件流。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AppTaskLogPublisher {

    private final AppChatMessageService appChatMessageService;
    private final AppTaskSseBroker appTaskSseBroker;
    private final ObjectMapper objectMapper;

    public AppChatMessage appendMessage(
            Long appId,
            Long taskId,
            AppChatMessageRole role,
            AppChatMessageType messageType,
            String content
    ) {
        return appendMessage(appId, taskId, role, messageType, content, null);
    }

    public AppChatMessage appendMessage(
            Long appId,
            Long taskId,
            AppChatMessageRole role,
            AppChatMessageType messageType,
            String content,
            Map<String, Object> metadata
    ) {
        AppChatMessage message = AppChatMessage.builder()
                .appId(appId)
                .taskId(taskId)
                .role(role.name())
                .messageType(messageType.name())
                .content(content == null ? "" : content)
                .metadata(toJson(metadata))
                .createdAt(LocalDateTime.now())
                .build();
        appChatMessageService.save(message);
        appTaskSseBroker.publish(taskId, "message", TaskStreamEvent.message(message));
        return message;
    }

    public void publishTransientMessage(
            Long appId,
            Long taskId,
            AppChatMessageRole role,
            AppChatMessageType messageType,
            String content,
            Map<String, Object> metadata
    ) {
        publishTransientMessage(appId, taskId, role, messageType, content, metadata, "command-log");
    }

    public void publishTransientMessage(
            Long appId,
            Long taskId,
            AppChatMessageRole role,
            AppChatMessageType messageType,
            String content,
            Map<String, Object> metadata,
            String eventName
    ) {
        TaskStreamEvent event = TaskStreamEvent.builder()
                .eventType(eventName)
                .appId(appId)
                .taskId(taskId)
                .role(role.name())
                .messageType(messageType.name())
                .content(content == null ? "" : content)
                .metadata(toJson(metadata))
                .createdAt(LocalDateTime.now())
                .build();
        appTaskSseBroker.publish(taskId, eventName, event);
    }

    public List<AppChatMessage> listTaskMessages(Long taskId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_CHAT_MESSAGE.ALL_COLUMNS)
                .from(APP_CHAT_MESSAGE)
                .where(APP_CHAT_MESSAGE.TASK_ID.eq(taskId))
                .orderBy(APP_CHAT_MESSAGE.CREATED_AT.asc())
                .orderBy(APP_CHAT_MESSAGE.ID.asc());
        return appChatMessageService.list(query);
    }

    public void publishState(AppTask task) {
        appTaskSseBroker.publish(task.getId(), "state", TaskStreamEvent.state(task));
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
