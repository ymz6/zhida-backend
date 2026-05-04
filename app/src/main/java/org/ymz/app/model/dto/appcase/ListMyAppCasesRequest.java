package org.ymz.app.model.dto.appcase;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 分页查询我的案例投稿。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListMyAppCasesRequest extends PageQuery {

    private String status;
}
