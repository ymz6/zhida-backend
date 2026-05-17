package org.ymz.app.service;

import com.mybatisflex.core.service.IService;
import org.ymz.app.model.dto.app.AppVO;
import org.ymz.app.model.dto.favorite.AddFavoriteAppRequest;
import org.ymz.app.model.dto.favorite.CreateFavoriteRequest;
import org.ymz.app.model.dto.favorite.FavoriteStatusVO;
import org.ymz.app.model.dto.favorite.FavoriteVO;
import org.ymz.app.model.dto.favorite.ListFavoriteAppsRequest;
import org.ymz.app.model.dto.favorite.MoveFavoriteAppRequest;
import org.ymz.app.model.dto.favorite.SortFavoritesRequest;
import org.ymz.app.model.dto.favorite.UpdateFavoriteRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.Favorite;

import java.util.List;

/**
 * 收藏夹服务层。
 *
 * @author ymz
 */
public interface FavoriteService extends IService<Favorite> {

    void createDefaultFavorite(Long userId);

    FavoriteVO createFavorite(Long userId, CreateFavoriteRequest request);

    List<FavoriteVO> listFavorites(Long userId);

    FavoriteVO getFavorite(Long userId, Long favoriteId);

    FavoriteVO updateFavorite(Long userId, Long favoriteId, UpdateFavoriteRequest request);

    void deleteFavorite(Long userId, Long favoriteId);

    void sortFavorites(Long userId, SortFavoritesRequest request);

    void addFavoriteApp(Long userId, Long appId, AddFavoriteAppRequest request);

    FavoriteStatusVO getFavoriteStatus(Long userId, Long appId);

    void removeFavoriteApp(Long userId, Long favoriteId, Long appId);

    PageResult<AppVO> listFavoriteApps(Long userId, Long favoriteId, ListFavoriteAppsRequest request);

    void moveFavoriteApp(Long userId, Long sourceFavoriteId, MoveFavoriteAppRequest request);
}
