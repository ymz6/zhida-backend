package org.ymz.app.model.dto.monitoring;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一监控查询请求。
 *
 * @author ymz
 */
@Data
public class MonitoringQueryRequest {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    @Min(value = 1, message = "非法指标步长")
    private Long stepSeconds;

    @Valid
    private List<MonitoringMetricQuery> metricQueries = new ArrayList<>();

    @Valid
    private List<MonitoringTableQuery> tableQueries = new ArrayList<>();

    @Schema(hidden = true)
    @AssertTrue(message = "时间范围不合法")
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || !startTime.isAfter(endTime);
    }
}
