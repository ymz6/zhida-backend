package org.ymz.app.model.dto.favorite;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移动收藏应用请求。
 *
 * @author ymz
 */
@Data
public class MoveFavoriteAppRequest {

    @NotNull(message = "目标收藏夹 ID 不能为空")
    private Long targetFavoriteId;

    @NotNull(message = "应用 ID 不能为空")
    private Long appId;
}
