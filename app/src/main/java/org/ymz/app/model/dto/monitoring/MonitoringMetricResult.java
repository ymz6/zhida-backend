package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一监控指标查询结果。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringMetricResult {

    private String queryId;

    private String key;

    private String title;

    private String unit;

    private String source;

    private String status;

    private Double latestValue;

    @Builder.Default
    private List<MonitoringSeries> series = new ArrayList<>();

    private String errorMessage;
}
