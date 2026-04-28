package org.ymz.app.model.dto.app;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 分页查询应用任务历史。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListAppTasksRequest extends PageQuery {
}
