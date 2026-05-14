package org.ymz.app.model.dto.page;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 游标查询请求基类。
 *
 * @author ymz
 */
@Data
public class CursorQuery {

    /**
     * 游标为空时查询最新一批数据；不为空时查询游标之前的数据。
     */
    private String cursor;

    @Min(value = 1, message = "非法查询数量")
    private int pageSize = 10;
}
