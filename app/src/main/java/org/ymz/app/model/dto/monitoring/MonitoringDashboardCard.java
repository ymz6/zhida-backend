package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 监控仪表盘指标卡片。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringDashboardCard {

    private String key;

    private String title;

    private String unit;

    private String source;

    private String status;

    private Double value;

    private String errorMessage;
}
