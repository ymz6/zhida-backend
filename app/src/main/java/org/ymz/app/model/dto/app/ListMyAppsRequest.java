package org.ymz.app.model.dto.app;

import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.SortablePageQuery;

import static org.ymz.app.model.entity.table.AppTableDef.APP;

/**
 * 分页查询我的应用列表。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListMyAppsRequest extends SortablePageQuery {

    private String keyword;

    private String status;

    private String deployStatus;

    @Override
    public QueryColumn resolveSortColumn() {
        if (!hasSort()) {
            return null;
        }
        return switch (getSortField()) {
            case "createdAt" -> APP.CREATED_AT;
            default -> null;
        };
    }
}
