package org.ymz.app.model.dto.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.entity.AppTaskEvent;

import java.time.LocalDateTime;

/**
 * 应用对话流推送数据。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppStreamEvent {

    private String eventType;

    private Long appId;

    private String status;

    private String currentStep;

    private Long messageId;

    private Long taskEventId;

    private String role;

    private String contentType;

    private String content;

    private String metadata;

    private LocalDateTime createdAt;

    public static AppStreamEvent message(AppChatMessage message) {
        return message(message, "message");
    }

    public static AppStreamEvent message(AppChatMessage message, String eventType) {
        return AppStreamEvent.builder()
                .eventType(eventType)
                .appId(message.getAppId())
                .messageId(message.getId())
                .role(message.getRole())
                .contentType(message.getContentType())
                .content(message.getContent())
                .metadata(message.getMetadata())
                .createdAt(message.getCreatedAt())
                .build();
    }

    public static AppStreamEvent assistantDelta(Long appId, String content) {
        return AppStreamEvent.builder()
                .eventType("assistant.delta")
                .appId(appId)
                .role("ASSISTANT")
                .contentType("TEXT")
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static AppStreamEvent taskEvent(AppTaskEvent event) {
        return AppStreamEvent.builder()
                .eventType(event.getEventType())
                .appId(event.getAppId())
                .taskEventId(event.getId())
                .content(event.getContent())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public static AppStreamEvent state(AppTask task) {
        return AppStreamEvent.builder()
                .eventType("state")
                .appId(task.getAppId())
                .status(task.getStatus())
                .currentStep(task.getCurrentStep())
                .build();
    }
}
