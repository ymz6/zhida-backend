package org.ymz.app.model.dto.appcase;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员案例信息。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminAppCaseInfo extends MyAppCaseInfo {

    private Long userId;

    private Long reviewerId;
}
