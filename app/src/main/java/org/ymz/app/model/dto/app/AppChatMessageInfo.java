package org.ymz.app.model.dto.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用对话消息信息。
 *
 * @author ymz
 */
@Data
public class AppChatMessageInfo {

    private Long id;

    private Long appId;

    private String role;

    private String contentType;

    private String content;

    private String metadata;

    private LocalDateTime createdAt;
}
