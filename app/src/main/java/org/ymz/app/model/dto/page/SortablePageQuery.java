package org.ymz.app.model.dto.page;

import com.mybatisflex.core.query.QueryColumn;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 可排序的列表分页查询请求基类
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class SortablePageQuery extends PageQuery {
    private String sortField;

    public enum SortDirection {
        ASC, DESC
    }

    private SortDirection sortOrder = SortDirection.DESC;

    public boolean hasSort() {
        return sortField != null && !sortField.isBlank();
    }

    /**
     * 子类负责把前端排序字段解析成真实排序列
     */
    public abstract QueryColumn resolveSortColumn();

}
