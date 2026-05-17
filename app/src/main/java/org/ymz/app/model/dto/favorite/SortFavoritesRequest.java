package org.ymz.app.model.dto.favorite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量更新收藏夹排序请求。
 *
 * @author ymz
 */
@Data
public class SortFavoritesRequest {

    @Valid
    @NotEmpty(message = "排序列表不能为空")
    private List<SortFavoriteItemRequest> favorites;
}
