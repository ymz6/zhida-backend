package org.ymz.app.model.dto.monitoring;

import lombok.Data;

/**
 * 单个模型的调用次数。
 *
 * @author ymz
 */
@Data
public class LlmModelCallCountVO {

    private String modelName;

    private Long callCount;
}
