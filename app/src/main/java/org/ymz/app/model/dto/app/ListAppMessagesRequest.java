package org.ymz.app.model.dto.app;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 游标查询应用消息历史。
 *
 * @author ymz
 */
@Data
public class ListAppMessagesRequest {

    private Long taskId;

    @Min(value = 1, message = "非法查询数量")
    @Max(value = 100, message = "非法查询数量")
    private int limit = 50;

    @Min(value = 1, message = "非法游标")
    private Long before;
}
