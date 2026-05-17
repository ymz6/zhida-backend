package org.ymz.app.model.dto.favorite;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 应用收藏状态。
 *
 * @author ymz
 */
@Data
@Builder
public class FavoriteStatusVO {

    private Long appId;

    private Boolean isFavorited;

    private List<FavoriteVO> favorites;
}
