package org.ymz.app.model.dto.monitoring;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.ymz.app.model.dto.page.PageQuery;

import java.time.LocalDateTime;

/**
 * 分页查询 LLM 调用日志请求。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListLlmLogsRequest extends PageQuery {

    /**
     * 创建时间开始，包含该时间点。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /**
     * 创建时间结束，不包含该时间点。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    @Schema(hidden = true)
    @AssertTrue(message = "时间范围不合法")
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || startTime.isBefore(endTime);
    }
}
