package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用状态
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppStatus {

    CREATING("创建中"),
    GENERATING("生成中"),
    BUILDING("构建中"),
    READY("可使用"),
    ITERATING("迭代中"),
    FAILED("失败");

    private final String description;
}
