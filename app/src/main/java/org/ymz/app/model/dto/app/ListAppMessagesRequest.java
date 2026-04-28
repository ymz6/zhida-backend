package org.ymz.app.model.dto.app;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 分页查询应用消息历史。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListAppMessagesRequest extends PageQuery {

    private Long taskId;
}
