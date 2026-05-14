package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用审核流程状态。
 *
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppAuditStatus {

    DRAFT(0),
    PENDING(1),
    APPROVED(2),
    REJECTED(3),
    WITHDRAWN(4);

    private final Integer code;
}
