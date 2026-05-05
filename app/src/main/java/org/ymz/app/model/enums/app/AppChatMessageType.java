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
    BUILD_LOG("命令输出流事件"),
    ERROR("错误信息");

    private final String description;
}
