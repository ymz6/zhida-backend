package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一监控查询结果。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringQueryResult {

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private long stepSeconds;

    @Builder.Default
    private List<MonitoringMetricResult> metrics = new ArrayList<>();

    @Builder.Default
    private List<MonitoringTableResult> tables = new ArrayList<>();
}
