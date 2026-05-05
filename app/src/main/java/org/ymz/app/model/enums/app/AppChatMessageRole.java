package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用对话消息角色
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppChatMessageRole {

    USER("用户消息"),
    ASSISTANT("AI 回复"),
    TOOL("工具或命令的瞬时事件"),
    SYSTEM("系统消息");

    private final String description;
}
