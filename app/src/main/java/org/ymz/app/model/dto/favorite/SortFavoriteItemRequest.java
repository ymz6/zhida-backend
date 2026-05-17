package org.ymz.app.model.dto.favorite;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单个收藏夹排序项。
 *
 * @author ymz
 */
@Data
public class SortFavoriteItemRequest {

    @NotNull(message = "收藏夹 ID 不能为空")
    private Long favoriteId;

    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder;
}
