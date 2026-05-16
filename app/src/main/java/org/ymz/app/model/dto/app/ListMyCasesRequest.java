package org.ymz.app.model.dto.app;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;
import org.ymz.app.model.enums.app.AppAuditStatus;

/**
 * 分页查询我的案例列表。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListMyCasesRequest extends PageQuery {

    /**
     * 审核状态，不传时查询全部状态。
     */
    private AppAuditStatus status;
}
