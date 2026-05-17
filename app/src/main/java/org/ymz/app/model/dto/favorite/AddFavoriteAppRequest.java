package org.ymz.app.model.dto.favorite;

import lombok.Data;

/**
 * 添加应用收藏请求。
 *
 * @author ymz
 */
@Data
public class AddFavoriteAppRequest {

    /**
     * 不传时默认加入默认收藏夹
     */
    private Long favoriteId;
}
