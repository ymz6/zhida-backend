package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 监控时间序列数据点。
 *
 * @author ymz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringPoint {

    private LocalDateTime time;

    private Double value;
}
