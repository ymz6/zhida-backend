package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用案例审核状态。
 *
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppCaseStatus {

    PENDING("待审核"),
    APPROVED("已公开"),
    REJECTED("已驳回"),
    OFFLINE("已下架");

    private final String description;
}
