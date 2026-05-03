package org.ymz.app.model.dto.monitoring;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 统一监控指标查询项。
 *
 * @author ymz
 */
@Data
public class MonitoringMetricQuery {

    @NotBlank(message = "指标查询 ID 不能为空")
    private String queryId;

    @NotBlank(message = "指标 Key 不能为空")
    private String key;
}
