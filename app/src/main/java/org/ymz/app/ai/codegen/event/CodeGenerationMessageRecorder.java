package org.ymz.app.ai.codegen.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.model.dto.app.AppStreamEvent;
import org.ymz.app.model.dto.app.ChatMessageMetadata;
import org.ymz.app.model.dto.app.content.ContentBlock;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageContentType;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.service.AppChatMessageService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.ymz.app.model.entity.table.AppChatMessageTableDef.APP_CHAT_MESSAGE;
import static org.ymz.app.model.enums.app.AppChatMessageRole.ASSISTANT;
import static org.ymz.app.model.enums.app.AppChatMessageRole.USER;

/**
 * 写入任务日志，并同步推送给已连接的事件流。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class CodeGenerationMessageRecorder {

    private final AppChatMessageService appChatMessageService;
    private final CodeGenerationTaskSseBroker appTaskSseBroker;
    private final ObjectMapper objectMapper;

    public AppChatMessage appendMessage(
            Long appId,
            Long taskId,
            AppChatMessageRole role,
            String content
    ) {
        return appendMessage(appId, taskId, role, content, null);
    }

    public AppChatMessage appendMessage(
            Long appId,
            Long taskId,
            AppChatMessageRole role,
            String content,
            Map<String, Object> metadata
    ) {
        AppChatMessage message = saveMessage(
                appId,
                taskId,
                role,
                AppChatMessageContentType.TEXT,
                content,
                metadata
        );
        appTaskSseBroker.publish(taskId, "message", AppStreamEvent.message(message));
        return message;
    }

    public AppChatMessage saveMessage(
            Long appId,
            Long taskId,
            AppChatMessageRole role,
            AppChatMessageContentType contentType,
            String content,
            Object metadata
    ) {
        AppChatMessage message = AppChatMessage.builder()
                .appId(appId)
                .taskId(taskId)
                .role(role.name())
                .contentType(contentType.name())
                .content(content == null ? "" : content)
                .metadata(toJson(metadata))
                .createdAt(LocalDateTime.now())
                .build();
        appChatMessageService.save(message);
        return message;
    }

    public void publishMessage(Long taskId, String eventName, AppChatMessage message) {
        appTaskSseBroker.publish(taskId, eventName, AppStreamEvent.message(message, eventName));
    }

    public void publishTransientMessage(
            Long appId,
            Long taskId,
            String content,
            Map<String, Object> metadata
    ) {
        publishTransientMessage(appId, taskId, content, metadata, "command-log");
    }

    public void publishTransientMessage(
            Long appId,
            Long taskId,
            String content,
            Map<String, Object> metadata,
            String eventName
    ) {
        AppStreamEvent event = AppStreamEvent.builder()
                .eventType(eventName)
                .appId(appId)
                .contentType(AppChatMessageContentType.TEXT.name())
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
                .and(APP_CHAT_MESSAGE.ROLE.in(List.of(USER.name(), ASSISTANT.name())))
                .orderBy(APP_CHAT_MESSAGE.CREATED_AT.asc())
                .orderBy(APP_CHAT_MESSAGE.ID.asc());
        return appChatMessageService.list(query);
    }

    public AppChatMessage getLastAssistantMessage(Long taskId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_CHAT_MESSAGE.ALL_COLUMNS)
                .from(APP_CHAT_MESSAGE)
                .where(APP_CHAT_MESSAGE.TASK_ID.eq(taskId))
                .and(APP_CHAT_MESSAGE.ROLE.eq(ASSISTANT.name()))
                .orderBy(APP_CHAT_MESSAGE.ID.desc())
                .limit(1);
        List<AppChatMessage> messages = appChatMessageService.list(query);
        return messages.isEmpty() ? null : messages.getFirst();
    }

    public void updateMetadata(Long messageId, ChatMessageMetadata metadata) {
        appChatMessageService.updateById(AppChatMessage.builder()
                .id(messageId)
                .metadata(toJson(metadata))
                .build());
    }

    public String serializeBlocks(List<ContentBlock> blocks) {
        try {
            return objectMapper.writerFor(new TypeReference<List<ContentBlock>>() {
            }).writeValueAsString(blocks);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化助手消息内容块", e);
        }
    }

    public void publishState(AppTask task) {
        appTaskSseBroker.publish(task.getId(), "state", AppStreamEvent.state(task));
    }

    private String toJson(Object metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{\"metadataError\":\"failed to serialize metadata\"}";
        }
    }
}
