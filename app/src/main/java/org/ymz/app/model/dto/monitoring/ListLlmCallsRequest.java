package org.ymz.app.model.dto.monitoring;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.ymz.app.model.dto.page.PageQuery;

import java.time.LocalDateTime;

/**
 * LLM 调用明细分页查询请求。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListLlmCallsRequest extends PageQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    private String scenario;

    private String modelName;

    private String status;

    private String finishReason;

    private String errorType;

    private Long appId;

    private Long taskId;

    @Schema(hidden = true)
    @AssertTrue(message = "时间范围不合法")
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || !startTime.isAfter(endTime);
    }
}
