package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用任务状态
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppTaskStatus {

    PENDING("待执行"),
    RUNNING("执行中"),
    SUCCESS("执行成功"),
    FAILED("执行失败"),
    CANCELED("已取消");

    private final String description;
}
