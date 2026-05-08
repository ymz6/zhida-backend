package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用任务步骤
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppTaskStep {

    INITIALIZING_WORKSPACE("正在初始化工作区"),
    GENERATING_CODE("正在生成代码"),
    CHATTING("正在对话"),
    BUILDING("正在构建应用"),
    DEPLOYING("正在部署应用"),
    FINISHED("已完成");

    private final String description;
}
