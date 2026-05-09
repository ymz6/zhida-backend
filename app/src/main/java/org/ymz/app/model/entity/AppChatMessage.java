package org.ymz.app.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 任务 ID，可为空，普通历史消息可以不绑定具体任务
     */
    private Long taskId;

    /**
     * 消息角色：USER-用户，ASSISTANT-AI 助手，TOOL-工具
     */
    private String role;

    /**
     * 内容类型：TEXT-纯文本，BLOCKS-结构化内容块 JSON
     */
    private String contentType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息附加信息，JSON 字符串格式，例如工具名称、文件路径、构建状态等
     */
    private String metadata;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
