package org.ymz.app.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用对话消息表 实体类。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_chat_message")
public class AppChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 应用 ID
     */
    private Long appId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 消息角色：USER-用户，ASSISTANT-AI 助手
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
