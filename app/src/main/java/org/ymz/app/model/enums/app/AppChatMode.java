package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用对话模式。
 *
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppChatMode {

    /**
     * 代码生成模式：建任务、跑代码生成 agent。首次调用即首次生成，后续即迭代。
     */
    CODE("代码生成"),

    /**
     * 对话答疑模式：建任务、跑只读 agent，不修改工作区。
     */
    CHAT("对话答疑"),

    /**
     * 重连当前任务流：不创建新任务、不发送新消息。
     */
    RESUME("重连流");

    private final String description;
}
