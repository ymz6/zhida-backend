package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用对话消息内容类型。
 *
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppChatMessageContentType {

    TEXT("纯文本"),
    BLOCKS("结构化内容块 JSON");

    private final String description;
}
