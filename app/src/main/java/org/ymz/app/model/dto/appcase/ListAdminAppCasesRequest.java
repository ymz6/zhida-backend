package org.ymz.app.model.dto.appcase;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 管理员分页查询案例列表。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListAdminAppCasesRequest extends PageQuery {

    private String status;

    private String keyword;

    private Boolean featured;
}
