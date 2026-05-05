package org.ymz.app.model.dto.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTaskEvent;
import org.ymz.app.model.entity.AppTask;

import java.time.LocalDateTime;

/**
 * 任务事件流推送数据。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStreamEvent {

    private String eventType;

    private Long appId;

    private Long taskId;

    private String status;

    private String currentStep;

    private Long messageId;

    private Long taskEventId;

    private String role;

    private String messageType;

    private String content;

    private String metadata;

    private LocalDateTime createdAt;

    public static TaskStreamEvent message(AppChatMessage message) {
        return TaskStreamEvent.builder()
                .eventType("message")
                .appId(message.getAppId())
                .taskId(message.getTaskId())
                .messageId(message.getId())
                .role(message.getRole())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .metadata(message.getMetadata())
                .createdAt(message.getCreatedAt())
                .build();
    }

    public static TaskStreamEvent assistantDelta(Long appId, Long taskId, String content) {
        return TaskStreamEvent.builder()
                .eventType("assistant.delta")
                .appId(appId)
                .taskId(taskId)
                .role("ASSISTANT")
                .messageType("CHAT")
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static TaskStreamEvent taskEvent(AppTaskEvent event) {
        return TaskStreamEvent.builder()
                .eventType(event.getEventType())
                .appId(event.getAppId())
                .taskId(event.getTaskId())
                .taskEventId(event.getId())
                .content(event.getContent())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public static TaskStreamEvent state(AppTask task) {
        return TaskStreamEvent.builder()
                .eventType("state")
                .appId(task.getAppId())
                .taskId(task.getId())
                .status(task.getStatus())
                .currentStep(task.getCurrentStep())
                .build();
    }
}
