package org.ymz.app.model.dto.monitoring;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * LLM 调用概览查询请求。
 *
 * @author ymz
 */
@Data
public class LlmLogOverviewRequest {

    /**
     * 统计开始时间，包含该时间点。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /**
     * 统计结束时间，不包含该时间点。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    @Schema(hidden = true)
    @AssertTrue(message = "时间范围不合法")
    public boolean isTimeRangeValid() {
        if (startTime == null || endTime == null) {
            return startTime == null && endTime == null;
        }
        return startTime.isBefore(endTime);
    }
}
