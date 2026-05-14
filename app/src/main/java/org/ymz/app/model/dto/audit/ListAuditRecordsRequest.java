package org.ymz.app.model.dto.audit;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;
import org.ymz.app.model.enums.app.AppAuditStatus;

/**
 * 分页查询审核记录请求。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListAuditRecordsRequest extends PageQuery {

    /**
     * 审核记录状态。
     */
    private AppAuditStatus status;
}
