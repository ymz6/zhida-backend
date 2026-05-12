package org.ymz.app.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.AppConverter;
import org.ymz.app.model.dto.app.AppVO;
import org.ymz.app.model.dto.app.ListAppsRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.User;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final AppConverter appConverter;

    public PageResult<AppVO> listApps(ListAppsRequest request) {
        // 先分页查询应用，再批量查询本页涉及的作者，最后用 MapStruct 组装 AppVO。
        // 这样既保留 App 与 User 各自的转换逻辑，也避免列表页出现 N+1 次用户查询。
        QueryWrapper query = QueryWrapper.create()
                .select(APP.ALL_COLUMNS)
                .from(APP)
                .orderBy(APP.CREATED_AT.desc());

        Page<App> page = appService.page(request.toPage(), query);
        List<Long> userIds = page.getRecords().stream()
                .map(App::getUserId)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return PageResult.of(page, app -> {
            // 分页场景批量加载作者，避免每条应用单独查询用户。
            User author = userMap.get(app.getUserId());
            return appConverter.toAppVO(app, author);
        });
    }

    public AppVO getApp(Long appId) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }

        User author = userService.getById(app.getUserId());
        return appConverter.toAppVO(app, author);
    }

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
