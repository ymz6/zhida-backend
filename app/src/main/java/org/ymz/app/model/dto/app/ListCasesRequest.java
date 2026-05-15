package org.ymz.app.model.dto.app;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 分页查询案例广场应用列表。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListCasesRequest extends PageQuery {

    /**
     * 应用名称关键词
     */
    private String keyword;

    /**
     * 是否仅查询精选案例
     */
    private Boolean featuredOnly = false;
}
