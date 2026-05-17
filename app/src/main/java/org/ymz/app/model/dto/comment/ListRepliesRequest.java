package org.ymz.app.model.dto.comment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 分页查询楼中楼回复。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListRepliesRequest extends PageQuery {
}
