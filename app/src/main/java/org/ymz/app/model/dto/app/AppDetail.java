package org.ymz.app.model.dto.app;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用详情。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AppDetail extends AppSummary {

    private String initPrompt;

    private String deployErrorMessage;
}
