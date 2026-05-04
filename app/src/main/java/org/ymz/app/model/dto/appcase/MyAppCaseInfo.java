package org.ymz.app.model.dto.appcase;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 我的案例投稿信息。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyAppCaseInfo extends AppCaseSummary {

    private String status;

    private String reviewRemark;

    private LocalDateTime updatedAt;
}
