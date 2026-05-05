package org.ymz.app.model.dto.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 游标查询任务运行事件。
 *
 * @author ymz
 */
@Data
public class ListTaskEventsRequest {

    @Min(value = 1, message = "非法查询数量")
    @Max(value = 100, message = "非法查询数量")
    private int limit = 100;

    @Min(value = 1, message = "非法游标")
    private Long before;
}
