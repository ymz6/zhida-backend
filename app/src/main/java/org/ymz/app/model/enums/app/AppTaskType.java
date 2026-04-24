package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用任务类型
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppTaskType {

    CREATE("创建应用"),
    EDIT("修改应用"),
    DEPLOY("部署应用");

    private final String description;
}
