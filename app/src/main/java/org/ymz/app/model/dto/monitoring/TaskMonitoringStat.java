package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用任务监控统计。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMonitoringStat {

    private String taskType;

    private long total;

    private long pending;

    private long running;

    private long success;

    private long failed;

    private double successRate;

    private double failedRate;

    private long averageDurationMillis;
}
