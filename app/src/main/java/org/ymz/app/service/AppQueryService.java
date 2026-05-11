package org.ymz.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

//    public PageResult<AppSummary> listMyApps(Long userId, ListMyAppsRequest request) {
//        QueryColumn sortColumn = request.resolveSortColumn();
//        QueryWrapper query = QueryWrapper.create()
//                .select(APP.ALL_COLUMNS)
//                .from(APP)
//                .where(APP.USER_ID.eq(userId))
//                .and(APP.STATUS.eq(request.getStatus(), If::hasText))
//                .and(APP.DEPLOY_STATUS.eq(request.getDeployStatus(), If::hasText));
//
//        String keyword = StrUtil.trim(request.getKeyword());
//        if (StrUtil.isNotBlank(keyword)) {
//            query.and(new Brackets(APP.NAME.like(keyword).or(APP.INIT_PROMPT.like(keyword))));
//        }
//
//        if (sortColumn == null) {
//            query.orderBy(APP.CREATED_AT.desc());
//        } else {
//            query.orderBy(sortColumn, SortablePageQuery.SortDirection.ASC.equals(request.getSortOrder()));
//        }
//
//        Page<App> page = appService.page(request.toPage(), query);
//        AppAuthor author = toAppAuthor(userService.getById(userId));
//        return PageResult.of(page, app -> {
//            AppSummary summary = appConverter.toAppSummary(app);
//            summary.setAuthor(author);
//            return summary;
//        });
//    }

//    public AppDetail getApp(Long userId, Long appId) {
//        App app = getOwnedApp(userId, appId);
//        AppDetail detail = appConverter.toAppDetail(app);
//        detail.setAuthor(toAppAuthor(userService.getById(app.getUserId())));
//        return detail;
//    }

//    public CursorResult<AppChatMessageInfo> listAppMessages(
//            Long userId,
//            Long appId,
//            ListAppMessagesRequest request) {
//        getOwnedApp(userId, appId);
//
//        QueryWrapper query = QueryWrapper.create()
//                .select(APP_CHAT_MESSAGE.ALL_COLUMNS)
//                .from(APP_CHAT_MESSAGE)
//                .where(APP_CHAT_MESSAGE.APP_ID.eq(appId))
//                .and(APP_CHAT_MESSAGE.ROLE.in(visibleRoles()))
//                .and(APP_CHAT_MESSAGE.ID.lt(request.getBefore(), If::notNull))
//                .orderBy(APP_CHAT_MESSAGE.ID.desc())
//                .limit(request.getLimit() + 1);
//
//        List<AppChatMessage> messages = appChatMessageService.list(query);
//        boolean hasMore = messages.size() > request.getLimit();
//        if (hasMore) {
//            messages = messages.subList(0, request.getLimit());
//        }
//
//        List<AppChatMessageInfo> list = new ArrayList<>(messages.size());
//        for (int i = messages.size() - 1; i >= 0; i--) {
//            list.add(appConverter.toAppChatMessageInfo(messages.get(i)));
//        }
//        Long nextCursor = hasMore && !list.isEmpty() ? list.getFirst().getId() : null;
//        return CursorResult.of(list, nextCursor, hasMore);
//    }

//    private App getOwnedApp(Long userId, Long appId) {
//        App app = appService.getById(appId);
//        if (app == null) {
//            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
//        }
//        if (!userId.equals(app.getUserId())) {
//            throw BusinessException.of(ResultCode.NO_PERMISSION);
//        }
//        return app;
//    }

//    private AppAuthor toAppAuthor(User user) {
//        if (user == null) {
//            return null;
//        }
//
//        AppAuthor author = new AppAuthor();
//        author.setId(user.getId());
//        author.setNickname(user.getNickname());
//        author.setAvatar(user.getAvatar());
//        return author;
//    }

//    private List<String> visibleRoles() {
//        return List.of(USER.name(), ASSISTANT.name());
//    }
}
