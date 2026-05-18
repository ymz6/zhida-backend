package org.ymz.app.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.AppConverter;
import org.ymz.app.model.dto.app.AppChatMessageVO;
import org.ymz.app.model.dto.app.AppVO;
import org.ymz.app.model.dto.app.ListAppMessagesRequest;
import org.ymz.app.model.dto.app.ListAppsRequest;
import org.ymz.app.model.dto.app.ListCasesRequest;
import org.ymz.app.model.dto.app.ListMyCasesRequest;
import org.ymz.app.model.dto.page.CursorResult;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.enums.UserRole;
import org.ymz.app.model.enums.app.AppAuditStatus;
import org.ymz.app.security.AuthContext;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ymz.app.model.entity.table.AppChatMessageTableDef.APP_CHAT_MESSAGE;
import static org.ymz.app.model.entity.table.AppTableDef.APP;

/**
 * 应用生成前端查询服务实现。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppQueryService {

    private final AppService appService;
    private final UserService userService;
    private final AppChatMessageService appChatMessageService;
    private final AppConverter appConverter;
    private final UserFollowService userFollowService;
    private final AppUrlBuilder appUrlBuilder;

    public PageResult<AppVO> listApps(AuthContext authContext, ListAppsRequest request) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP.ALL_COLUMNS)
                .from(APP)
                .where(APP.USER_ID.eq(authContext.getUserId(), !UserRole.ADMIN.equals(authContext.getUserRole())))
                .orderBy(APP.CREATED_AT.desc());

        Page<App> page = appService.page(request.toPage(), query);
        return toAppPageResult(page, authContext.getUserId());
    }

    public AppVO getApp(AuthContext authContext, Long appId) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!app.getUserId().equals(authContext.getUserId())
                && !UserRole.ADMIN.equals(authContext.getUserRole())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }

        User author = userService.getById(app.getUserId());
        return toAppVO(app, author, authContext.getUserId());
    }

    public PageResult<AppVO> listCases(ListCasesRequest request) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP.ALL_COLUMNS)
                .from(APP)
                // 案例广场只展示审核通过的公开应用，并按可选参数过滤。
                .where(APP.AUDIT_STATUS.eq(AppAuditStatus.APPROVED.getCode()))
                .and(APP.DEPLOYED_AT.isNotNull())
                .and(APP.DEPLOY_KEY.isNotNull())
                .and(APP.DEPLOY_KEY.ne(""))
                .and(APP.NAME.like(request.getKeyword(), If::hasText))
                .orderBy(APP.FEATURED.desc(), APP.FEATURED_AT.desc(), APP.PUBLISHED_AT.desc());

        if (Boolean.TRUE.equals(request.getFeaturedOnly())) {
            query.and(APP.FEATURED.eq(true));
        }

        Page<App> page = appService.page(request.toPage(), query);
        return toAppPageResult(page, null);
    }

    public PageResult<AppVO> listMyCases(AuthContext authContext, ListMyCasesRequest request) {
        Integer statusCode = request.getStatus() == null ? null : request.getStatus().getCode();
        QueryWrapper query = QueryWrapper.create()
                .select(APP.ALL_COLUMNS)
                .from(APP)
                // 我的案例默认查当前用户全部应用，传入状态时按审核状态过滤。
                .where(APP.USER_ID.eq(authContext.getUserId()))
                .and(APP.AUDIT_STATUS.eq(statusCode, If::notNull))
                .orderBy(APP.CREATED_AT.desc());

        Page<App> page = appService.page(request.toPage(), query);
        return toAppPageResult(page, authContext.getUserId());
    }

    public AppVO getCase(Long appId) {
        // 案例广场只展示审核通过的公开应用。
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
            throw BusinessException.of(ResultCode.NOT_FOUND, "案例不存在");
        }

        User author = userService.getById(app.getUserId());
        return toAppVO(app, author, null);
    }

    public CursorResult<AppChatMessageVO> listAppMessages(
            AuthContext authContext,
            Long appId,
            ListAppMessagesRequest request) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!app.getUserId().equals(authContext.getUserId()) && !UserRole.ADMIN.equals(authContext.getUserRole())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }

        Long cursorId = null;
        if (request.getCursor() != null && !request.getCursor().isBlank()) {
            try {
                cursorId = Long.valueOf(request.getCursor());
            } catch (NumberFormatException e) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "游标参数不合法");
            }
        }

        QueryWrapper query = QueryWrapper.create()
                .select(APP_CHAT_MESSAGE.ALL_COLUMNS)
                .from(APP_CHAT_MESSAGE)
                .where(APP_CHAT_MESSAGE.APP_ID.eq(appId))
                .and(APP_CHAT_MESSAGE.ID.lt(cursorId, If::notNull))
                .orderBy(APP_CHAT_MESSAGE.ID.desc())
                .limit(request.getPageSize() + 1);

        // 多查一条只用于判断是否还有更早的聊天记录。
        List<AppChatMessage> messages = appChatMessageService.list(query);
        boolean hasMore = messages.size() > request.getPageSize();
        List<AppChatMessage> currentBatch = new ArrayList<>(
                hasMore ? messages.subList(0, request.getPageSize()) : messages);
        // 查询时按 id 倒序取更早消息，返回前反转为自然聊天顺序。
        Collections.reverse(currentBatch);

        List<AppChatMessageVO> list = currentBatch.stream()
                .map(appConverter::toAppChatMessageVO)
                .toList();
        String nextCursor = hasMore && !currentBatch.isEmpty()
                ? String.valueOf(currentBatch.getFirst().getId())
                : null;
        return CursorResult.of(list, nextCursor, hasMore);
    }

    private PageResult<AppVO> toAppPageResult(Page<App> page, Long currentUserId) {
        // 分页场景批量加载作者，避免每条应用单独查询用户。
        List<Long> userIds = page.getRecords().stream()
                .map(App::getUserId)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, Boolean> followingMap = currentUserId == null || userIds.isEmpty()
                ? Map.of()
                : userFollowService.batchGetFollowingStatus(currentUserId, userIds);
        Map<Long, Boolean> followedMap = currentUserId == null || userIds.isEmpty()
                ? Map.of()
                : userFollowService.batchGetFollowedStatus(currentUserId, userIds);

        return PageResult.of(page, app -> {
            User author = userMap.get(app.getUserId());
            AppVO vo = appConverter.toAppVO(app, author);
            fillAppUrls(vo, app);
            fillAuthorFollowStatus(vo, followingMap, followedMap);
            return vo;
        });
    }

    private AppVO toAppVO(App app, User author, Long currentUserId) {
        AppVO vo = appConverter.toAppVO(app, author);
        fillAppUrls(vo, app);
        if (currentUserId == null || author == null) {
            return vo;
        }
        List<Long> authorIds = List.of(author.getId());
        fillAuthorFollowStatus(
                vo,
                userFollowService.batchGetFollowingStatus(currentUserId, authorIds),
                userFollowService.batchGetFollowedStatus(currentUserId, authorIds));
        return vo;
    }

    private void fillAuthorFollowStatus(
            AppVO vo,
            Map<Long, Boolean> followingMap,
            Map<Long, Boolean> followedMap) {
        if (vo == null || vo.getAuthor() == null || vo.getAuthor().getId() == null) {
            return;
        }
        Long authorId = vo.getAuthor().getId();
        vo.getAuthor().setIsFollowing(Boolean.TRUE.equals(followingMap.get(authorId)));
        vo.getAuthor().setIsFollowed(Boolean.TRUE.equals(followedMap.get(authorId)));
    }

    private void fillAppUrls(AppVO vo, App app) {
        if (vo == null || app == null) {
            return;
        }
        vo.setDeployUrl(appUrlBuilder.buildDeployUrl(app.getDeployKey()));
    }
}
