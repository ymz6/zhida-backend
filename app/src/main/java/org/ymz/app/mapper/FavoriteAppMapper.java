package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Param;
import org.ymz.app.model.dto.favorite.FavoriteVO;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.FavoriteApp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    default Page<App> paginateFavoriteApps(
            Page<App> page,
            @Param("favoriteId") Long favoriteId,
            @Param("keyword") String keyword) {
        // 自定义 XML 分页必须走 xmlPaginate，避免 MyBatis 把 Page 返回值当单条记录处理。
        Map<String, Object> params = new HashMap<>();
        params.put("favoriteId", favoriteId);
        params.put("keyword", keyword);
        return xmlPaginate("paginateFavoriteApps", page, params);
    }

    List<FavoriteVO> listFavoritesByUserIdAndAppId(@Param("userId") Long userId, @Param("appId") Long appId);
}
