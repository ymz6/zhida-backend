package org.ymz.app.model.dto.appcase;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 分页查询公开案例列表。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListPublicAppCasesRequest extends PageQuery {

    private String keyword;
}
