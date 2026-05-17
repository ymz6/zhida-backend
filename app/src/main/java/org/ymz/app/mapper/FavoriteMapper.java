package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.ymz.app.model.dto.favorite.FavoriteVO;
import org.ymz.app.model.entity.Favorite;

import java.util.List;

/**
 * 收藏夹映射层。
 *
 * @author ymz
 */
public interface FavoriteMapper extends BaseMapper<Favorite> {

    Favorite selectDefaultByUserId(@Param("userId") Long userId);

    Favorite selectFavoriteById(@Param("favoriteId") Long favoriteId);

    long countNormalByUserId(@Param("userId") Long userId);

    long countByUserIdAndName(
            @Param("userId") Long userId,
            @Param("name") String name,
            @Param("excludeFavoriteId") Long excludeFavoriteId);

    List<FavoriteVO> listByUserIdWithAppCount(@Param("userId") Long userId);

    FavoriteVO selectFavoriteVO(@Param("userId") Long userId, @Param("favoriteId") Long favoriteId);
}
