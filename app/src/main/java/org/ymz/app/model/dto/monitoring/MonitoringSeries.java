package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 监控时间序列。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringSeries {

    private String name;

    @Builder.Default
    private List<MonitoringPoint> points = new ArrayList<>();
}
