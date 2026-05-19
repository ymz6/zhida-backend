package org.ymz.app.model.dto.app;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;
import org.ymz.app.model.enums.app.AppAuditStatus;

/**
 * 后台分页查询应用案例列表请求。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListAdminAppCasesRequest extends PageQuery {

    /**
     * 案例状态，通常为已公开或已下架。
     */
    private AppAuditStatus status;

    /**
     * 是否精选。
     */
    private Boolean featured;

    /**
     * 应用名称关键词。
     */
    private String keyword;
}
