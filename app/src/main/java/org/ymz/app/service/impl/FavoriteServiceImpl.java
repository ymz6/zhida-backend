package org.ymz.app.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ymz.app.converter.AppConverter;
import org.ymz.app.converter.FavoriteConverter;
import org.ymz.app.mapper.FavoriteAppMapper;
import org.ymz.app.mapper.FavoriteMapper;
import org.ymz.app.model.dto.app.AppVO;
import org.ymz.app.model.dto.favorite.AddFavoriteAppRequest;
import org.ymz.app.model.dto.favorite.CreateFavoriteRequest;
import org.ymz.app.model.dto.favorite.FavoriteStatusVO;
import org.ymz.app.model.dto.favorite.FavoriteVO;
import org.ymz.app.model.dto.favorite.ListFavoriteAppsRequest;
import org.ymz.app.model.dto.favorite.MoveFavoriteAppRequest;
import org.ymz.app.model.dto.favorite.SortFavoriteItemRequest;
import org.ymz.app.model.dto.favorite.SortFavoritesRequest;
import org.ymz.app.model.dto.favorite.UpdateFavoriteRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.Favorite;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.enums.app.AppAuditStatus;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppUrlBuilder;
import org.ymz.app.service.FavoriteService;
import org.ymz.app.service.UserFollowService;
import org.ymz.app.service.UserService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ymz.app.model.entity.table.AppTableDef.APP;

/**
 * 收藏夹服务层实现。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    private static final String DEFAULT_FAVORITE_NAME = "默认收藏夹";
    private static final int MAX_NORMAL_FAVORITE_COUNT = 20;

    private final FavoriteMapper favoriteMapper;
    private final FavoriteAppMapper favoriteAppMapper;
    private final AppService appService;
    private final UserService userService;
    private final UserFollowService userFollowService;
    private final FavoriteConverter favoriteConverter;
    private final AppConverter appConverter;
    private final AppUrlBuilder appUrlBuilder;

    @Override
    public void createDefaultFavorite(Long userId) {
        if (favoriteMapper.selectDefaultByUserId(userId) != null) {
            return;
        }

        Favorite favorite = Favorite.builder()
                .userId(userId)
                .name(DEFAULT_FAVORITE_NAME)
                .description("")
                .sortOrder(0)
                .isDefault(true)
                .createdAt(LocalDateTime.now())
                .build();
        if (favoriteMapper.insert(favorite) != 1) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "创建默认收藏夹失败");
        }
    }

    @Override
    public FavoriteVO createFavorite(Long userId, CreateFavoriteRequest request) {
        String name = StrUtil.trimToNull(request.getName());
        if (name == null) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "收藏夹名称不能为空");
        }
        if (favoriteMapper.countNormalByUserId(userId) >= MAX_NORMAL_FAVORITE_COUNT) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "收藏夹数量已达上限");
        }
        ensureFavoriteNameAvailable(userId, name, null);

        Favorite favorite = Favorite.builder()
                .userId(userId)
                .name(name)
                .description(StrUtil.nullToDefault(StrUtil.trimToNull(request.getDescription()), ""))
                .sortOrder(0)
                .isDefault(false)
                .createdAt(LocalDateTime.now())
                .build();
        if (favoriteMapper.insert(favorite) != 1) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "创建收藏夹失败");
        }
        return favoriteConverter.toFavoriteVO(favorite);
    }

    @Override
    public List<FavoriteVO> listFavorites(Long userId) {
        return favoriteMapper.listByUserIdWithAppCount(userId);
    }

    @Override
    public FavoriteVO getFavorite(Long userId, Long favoriteId) {
        requireOwnedFavorite(userId, favoriteId);
        FavoriteVO vo = favoriteMapper.selectFavoriteVO(userId, favoriteId);
        if (vo == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "收藏夹不存在");
        }
        return vo;
    }

    @Override
    public FavoriteVO updateFavorite(Long userId, Long favoriteId, UpdateFavoriteRequest request) {
        Favorite favorite = requireOwnedFavorite(userId, favoriteId);
        if (Boolean.TRUE.equals(favorite.getIsDefault())
                && (request.getName() != null || request.getDescription() != null)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "默认收藏夹不可修改名称或描述");
        }

        Favorite update = Favorite.builder()
                .id(favoriteId)
                .build();
        boolean changed = false;
        if (!Boolean.TRUE.equals(favorite.getIsDefault())) {
            String name = StrUtil.trimToNull(request.getName());
            if (request.getName() != null && name == null) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "收藏夹名称不能为空");
            }
            if (name != null) {
                ensureFavoriteNameAvailable(userId, name, favoriteId);
                update.setName(name);
                changed = true;
            }
            if (request.getDescription() != null) {
                update.setDescription(StrUtil.nullToDefault(StrUtil.trimToNull(request.getDescription()), ""));
                changed = true;
            }
        }
        if (request.getSortOrder() != null) {
            update.setSortOrder(request.getSortOrder());
            changed = true;
        }

        if (!changed) {
            return getFavorite(userId, favoriteId);
        }
        if (favoriteMapper.update(update) != 1) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "更新收藏夹失败");
        }
        return getFavorite(userId, favoriteId);
    }

    @Override
    @Transactional
    public void deleteFavorite(Long userId, Long favoriteId) {
        Favorite favorite = requireOwnedFavorite(userId, favoriteId);
        if (Boolean.TRUE.equals(favorite.getIsDefault())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "默认收藏夹不可删除");
        }

        favoriteAppMapper.deleteByFavoriteId(favoriteId);
        if (favoriteMapper.deleteById(favoriteId) != 1) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "删除收藏夹失败");
        }
    }

    @Override
    @Transactional
    public void sortFavorites(Long userId, SortFavoritesRequest request) {
        for (SortFavoriteItemRequest item : request.getFavorites()) {
            requireOwnedFavorite(userId, item.getFavoriteId());
            Favorite update = Favorite.builder()
                    .id(item.getFavoriteId())
                    .sortOrder(item.getSortOrder())
                    .build();
            if (favoriteMapper.update(update) != 1) {
                throw BusinessException.of(ResultCode.SYSTEM_ERROR, "更新收藏夹排序失败");
            }
        }
    }

    @Override
    @Transactional
    public void addFavoriteApp(Long userId, Long appId, AddFavoriteAppRequest request) {
        requirePublicCase(appId);
        Long favoriteId = request == null ? null : request.getFavoriteId();
        Favorite favorite = favoriteId == null
                ? requireDefaultFavorite(userId)
                : requireOwnedFavorite(userId, favoriteId);
        if (favoriteAppMapper.countByFavoriteIdAndAppId(favorite.getId(), appId) > 0) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "应用已在该收藏夹中");
        }
        // 唯一索引兜底处理并发重复添加，统一转换成明确业务提示。
        if (favoriteAppMapper.insertFavoriteApp(favorite.getId(), appId) != 1) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "应用已在该收藏夹中");
        }
    }

    @Override
    public FavoriteStatusVO getFavoriteStatus(Long userId, Long appId) {
        requirePublicCase(appId);
        List<FavoriteVO> favorites = favoriteAppMapper.listFavoritesByUserIdAndAppId(userId, appId);
        return FavoriteStatusVO.builder()
                .appId(appId)
                .isFavorited(!favorites.isEmpty())
                .favorites(favorites)
                .build();
    }

    @Override
    public void removeFavoriteApp(Long userId, Long favoriteId, Long appId) {
        requireOwnedFavorite(userId, favoriteId);
        favoriteAppMapper.deleteByFavoriteIdAndAppId(favoriteId, appId);
    }

    @Override
    public PageResult<AppVO> listFavoriteApps(Long userId, Long favoriteId, ListFavoriteAppsRequest request) {
        requireOwnedFavorite(userId, favoriteId);
        Page<App> page = favoriteAppMapper.paginateFavoriteApps(
                request.toPage(),
                favoriteId,
                StrUtil.trimToNull(request.getKeyword()));
        return toAppPageResult(page, userId);
    }

    @Override
    @Transactional
    public void moveFavoriteApp(Long userId, Long sourceFavoriteId, MoveFavoriteAppRequest request) {
        Favorite source = requireOwnedFavorite(userId, sourceFavoriteId);
        Favorite target = sourceFavoriteId.equals(request.getTargetFavoriteId())
                ? source
                : requireOwnedFavorite(userId, request.getTargetFavoriteId());
        Long appId = request.getAppId();
        if (favoriteAppMapper.countByFavoriteIdAndAppId(source.getId(), appId) == 0) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "收藏关系不存在");
        }
        if (source.getId().equals(target.getId())) {
            return;
        }
        if (favoriteAppMapper.countByFavoriteIdAndAppId(target.getId(), appId) == 0) {
            int inserted = favoriteAppMapper.insertFavoriteApp(target.getId(), appId);
            if (inserted != 1 && favoriteAppMapper.countByFavoriteIdAndAppId(target.getId(), appId) == 0) {
                throw BusinessException.of(ResultCode.SYSTEM_ERROR, "移动收藏失败");
            }
        }
        favoriteAppMapper.deleteByFavoriteIdAndAppId(source.getId(), appId);
    }

    private Favorite requireDefaultFavorite(Long userId) {
        Favorite favorite = favoriteMapper.selectDefaultByUserId(userId);
        if (favorite != null) {
            return favorite;
        }

        createDefaultFavorite(userId);
        favorite = favoriteMapper.selectDefaultByUserId(userId);
        if (favorite == null) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "默认收藏夹不存在");
        }
        return favorite;
    }

    private Favorite requireOwnedFavorite(Long userId, Long favoriteId) {
        Favorite favorite = favoriteMapper.selectFavoriteById(favoriteId);
        if (favorite == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "收藏夹不存在");
        }
        if (!favorite.getUserId().equals(userId)) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        return favorite;
    }

    private App requirePublicCase(Long appId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP.ALL_COLUMNS)
                .from(APP)
                .where(APP.ID.eq(appId))
                .and(APP.AUDIT_STATUS.eq(AppAuditStatus.APPROVED.getCode()))
                .and(APP.DEPLOYED_AT.isNotNull())
                .and(APP.DEPLOY_KEY.isNotNull())
                .and(APP.DEPLOY_KEY.ne(""));
        App app = appService.getOne(query);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "案例不存在或未公开");
        }
        return app;
    }

    private void ensureFavoriteNameAvailable(Long userId, String name, Long excludeFavoriteId) {
        if (favoriteMapper.countByUserIdAndName(userId, name, excludeFavoriteId) > 0) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "收藏夹名称已存在");
        }
    }

    private PageResult<AppVO> toAppPageResult(Page<App> page, Long currentUserId) {
        List<Long> userIds = page.getRecords().stream()
                .map(App::getUserId)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, Boolean> followingMap = userIds.isEmpty()
                ? Map.of()
                : userFollowService.batchGetFollowingStatus(currentUserId, userIds);
        Map<Long, Boolean> followedMap = userIds.isEmpty()
                ? Map.of()
                : userFollowService.batchGetFollowedStatus(currentUserId, userIds);

        return PageResult.of(page, app -> {
            AppVO vo = appConverter.toAppVO(app, userMap.get(app.getUserId()));
            if (vo != null) {
                vo.setDeployUrl(appUrlBuilder.buildDeployUrl(app.getDeployKey()));
                if (vo.getAuthor() != null) {
                    Long authorId = vo.getAuthor().getId();
                    vo.getAuthor().setIsFollowing(Boolean.TRUE.equals(followingMap.get(authorId)));
                    vo.getAuthor().setIsFollowed(Boolean.TRUE.equals(followedMap.get(authorId)));
                }
            }
            return vo;
        });
    }
}
