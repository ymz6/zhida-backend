package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用对话消息类型
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppChatMessageType {

    CHAT("普通聊天消息"),
    PLAN("AI 执行计划"),
    TOOL_CALL("工具调用"),
    TOOL_RESULT("工具调用结果"),
    BUILD_LOG("构建日志"),
    ERROR("错误信息");

    private final String description;
}
