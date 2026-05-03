package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 监控仪表盘。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringDashboard {

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String overallStatus;

    @Builder.Default
    private List<MonitoringDashboardCard> systemCards = new ArrayList<>();

    @Builder.Default
    private List<MonitoringDashboardCard> taskCards = new ArrayList<>();

    @Builder.Default
    private List<MonitoringDashboardCard> exceptionCards = new ArrayList<>();

    @Builder.Default
    private List<MonitoringDashboardCard> llmCards = new ArrayList<>();

    @Builder.Default
    private List<MonitoringMetricResult> charts = new ArrayList<>();
}
