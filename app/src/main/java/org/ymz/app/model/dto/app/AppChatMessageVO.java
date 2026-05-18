package org.ymz.app.model.dto.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用聊天消息展示对象。
 *
 * @author ymz
 */
@Data
public class AppChatMessageVO {

    private Long id;

    private String role;

    private String content;

    private LocalDateTime createdAt;
}
