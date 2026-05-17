package org.ymz.app.model.dto.favorite;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 分页查询收藏夹内应用请求。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListFavoriteAppsRequest extends PageQuery {

    /**
     * 应用名称关键词
     */
    private String keyword;
}
