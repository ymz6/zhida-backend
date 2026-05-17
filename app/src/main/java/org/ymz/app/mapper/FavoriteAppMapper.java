package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Param;
import org.ymz.app.model.dto.favorite.FavoriteVO;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.FavoriteApp;

import java.util.List;

/**
 * 收藏夹应用关联映射层。
 *
 * @author ymz
 */
public interface FavoriteAppMapper extends BaseMapper<FavoriteApp> {

    long countByFavoriteIdAndAppId(@Param("favoriteId") Long favoriteId, @Param("appId") Long appId);

    int insertFavoriteApp(@Param("favoriteId") Long favoriteId, @Param("appId") Long appId);

    int deleteByFavoriteId(@Param("favoriteId") Long favoriteId);

    int deleteByFavoriteIdAndAppId(@Param("favoriteId") Long favoriteId, @Param("appId") Long appId);

    Page<App> paginateFavoriteApps(
            Page<App> page,
            @Param("favoriteId") Long favoriteId,
            @Param("keyword") String keyword);

    List<FavoriteVO> listFavoritesByUserIdAndAppId(@Param("userId") Long userId, @Param("appId") Long appId);
}
