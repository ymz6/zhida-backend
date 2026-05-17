package org.ymz.app.model.dto.comment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 分页查询一级评论。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListCommentsRequest extends PageQuery {
}
