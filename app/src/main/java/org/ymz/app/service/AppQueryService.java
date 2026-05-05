package org.ymz.app.service;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.Brackets;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.AppConverter;
import org.ymz.app.model.dto.app.AppAuthor;
import org.ymz.app.model.dto.app.AppChatMessageInfo;
import org.ymz.app.model.dto.app.AppDetail;
import org.ymz.app.model.dto.app.AppSummary;
import org.ymz.app.model.dto.app.AppTaskInfo;
import org.ymz.app.model.dto.app.ListAppMessagesRequest;
import org.ymz.app.model.dto.app.ListAppTasksRequest;
import org.ymz.app.model.dto.app.ListMyAppsRequest;
import org.ymz.app.model.dto.page.CursorResult;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.dto.page.SortablePageQuery;
import org.ymz.app.model.dto.task.AppTaskEventInfo;
import org.ymz.app.model.dto.task.ListTaskEventsRequest;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTaskEvent;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.entity.User;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.ymz.app.model.entity.table.AppChatMessageTableDef.APP_CHAT_MESSAGE;
import static org.ymz.app.model.entity.table.AppTableDef.APP;
import static org.ymz.app.model.entity.table.AppTaskEventTableDef.APP_TASK_EVENT;
import static org.ymz.app.model.entity.table.AppTaskTableDef.APP_TASK;
import static org.ymz.app.model.enums.app.AppChatMessageRole.ASSISTANT;
import static org.ymz.app.model.enums.app.AppChatMessageRole.SYSTEM;
import static org.ymz.app.model.enums.app.AppChatMessageRole.USER;
import static org.ymz.app.model.enums.app.AppChatMessageType.CHAT;
import static org.ymz.app.model.enums.app.AppChatMessageType.ERROR;

/**
 * 应用生成前端查询服务实现。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppQueryService {

    private final AppService appService;
    private final AppTaskService appTaskService;
    private final AppTaskEventService appTaskEventService;
    private final AppChatMessageService appChatMessageService;
    private final UserService userService;
    private final AppConverter appConverter;

    public PageResult<AppSummary> listMyApps(Long userId, ListMyAppsRequest request) {
        QueryColumn sortColumn = request.resolveSortColumn();
        QueryWrapper query = QueryWrapper.create()
                .select(APP.ALL_COLUMNS)
                .from(APP)
                .where(APP.USER_ID.eq(userId))
                .and(APP.STATUS.eq(request.getStatus(), If::hasText))
                .and(APP.DEPLOY_STATUS.eq(request.getDeployStatus(), If::hasText));

        String keyword = StrUtil.trim(request.getKeyword());
        if (StrUtil.isNotBlank(keyword)) {
            query.and(new Brackets(APP.NAME.like(keyword).or(APP.INIT_PROMPT.like(keyword))));
        }

        if (sortColumn == null) {
            query.orderBy(APP.CREATED_AT.desc());
        } else {
            query.orderBy(sortColumn, SortablePageQuery.SortDirection.ASC.equals(request.getSortOrder()));
        }

        Page<App> page = appService.page(request.toPage(), query);
        AppAuthor author = toAppAuthor(userService.getById(userId));
        return PageResult.of(page, app -> {
            AppSummary summary = appConverter.toAppSummary(app);
            summary.setAuthor(author);
            return summary;
        });
    }

    public AppDetail getApp(Long userId, Long appId) {
        App app = getOwnedApp(userId, appId);
        AppDetail detail = appConverter.toAppDetail(app);
        detail.setAuthor(toAppAuthor(userService.getById(app.getUserId())));
        return detail;
    }

    public PageResult<AppTaskInfo> listAppTasks(Long userId, Long appId, ListAppTasksRequest request) {
        getOwnedApp(userId, appId);

        QueryWrapper query = QueryWrapper.create()
                .select(APP_TASK.ALL_COLUMNS)
                .from(APP_TASK)
                .where(APP_TASK.APP_ID.eq(appId))
                .and(APP_TASK.USER_ID.eq(userId))
                .orderBy(APP_TASK.CREATED_AT.desc())
                .orderBy(APP_TASK.ID.desc());

        Page<AppTask> page = appTaskService.page(request.toPage(), query);
        return PageResult.of(page, appConverter::toAppTaskInfo);
    }

    public CursorResult<AppChatMessageInfo> listAppMessages(
            Long userId,
            Long appId,
            ListAppMessagesRequest request) {
        getOwnedApp(userId, appId);
        Long taskId = request.getTaskId();
        if (taskId != null) {
            AppTask task = getOwnedTask(userId, taskId);
            if (!appId.equals(task.getAppId())) {
                throw BusinessException.of(ResultCode.NOT_FOUND, "任务不存在");
            }
        }

        QueryWrapper query = QueryWrapper.create()
                .select(APP_CHAT_MESSAGE.ALL_COLUMNS)
                .from(APP_CHAT_MESSAGE)
                .where(APP_CHAT_MESSAGE.APP_ID.eq(appId))
                .and(APP_CHAT_MESSAGE.ROLE.in(visibleRoles()))
                .and(APP_CHAT_MESSAGE.MESSAGE_TYPE.in(visibleMessageTypes()))
                .and(APP_CHAT_MESSAGE.TASK_ID.eq(taskId, If::notNull))
                .and(APP_CHAT_MESSAGE.ID.lt(request.getBefore(), If::notNull))
                .orderBy(APP_CHAT_MESSAGE.ID.desc())
                .limit(request.getLimit() + 1);

        List<AppChatMessage> messages = appChatMessageService.list(query);
        boolean hasMore = messages.size() > request.getLimit();
        if (hasMore) {
            messages = messages.subList(0, request.getLimit());
        }

        List<AppChatMessageInfo> list = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            list.add(appConverter.toAppChatMessageInfo(messages.get(i)));
        }
        Long nextCursor = hasMore && !list.isEmpty() ? list.getFirst().getId() : null;
        return CursorResult.of(list, nextCursor, hasMore);
    }

    public CursorResult<AppTaskEventInfo> listTaskEvents(Long userId, Long taskId, ListTaskEventsRequest request) {
        AppTask task = getOwnedTask(userId, taskId);

        QueryWrapper query = QueryWrapper.create()
                .select(APP_TASK_EVENT.ALL_COLUMNS)
                .from(APP_TASK_EVENT)
                .where(APP_TASK_EVENT.TASK_ID.eq(taskId))
                .and(APP_TASK_EVENT.APP_ID.eq(task.getAppId()))
                .and(APP_TASK_EVENT.ID.lt(request.getBefore(), If::notNull))
                .orderBy(APP_TASK_EVENT.ID.desc())
                .limit(request.getLimit() + 1);

        List<AppTaskEvent> events = appTaskEventService.list(query);
        boolean hasMore = events.size() > request.getLimit();
        if (hasMore) {
            events = events.subList(0, request.getLimit());
        }

        List<AppTaskEventInfo> list = new ArrayList<>(events.size());
        for (int i = events.size() - 1; i >= 0; i--) {
            list.add(appConverter.toAppTaskEventInfo(events.get(i)));
        }
        Long nextCursor = hasMore && !list.isEmpty() ? list.getFirst().getId() : null;
        return CursorResult.of(list, nextCursor, hasMore);
    }

    public AppTaskInfo getTask(Long userId, Long taskId) {
        return appConverter.toAppTaskInfo(getOwnedTask(userId, taskId));
    }

    private App getOwnedApp(Long userId, Long appId) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!userId.equals(app.getUserId())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        return app;
    }

    private AppTask getOwnedTask(Long userId, Long taskId) {
        AppTask task = appTaskService.getById(taskId);
        if (task == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "任务不存在");
        }
        if (!userId.equals(task.getUserId())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        return task;
    }

    private AppAuthor toAppAuthor(User user) {
        if (user == null) {
            return null;
        }

        AppAuthor author = new AppAuthor();
        author.setId(user.getId());
        author.setNickname(user.getNickname());
        author.setAvatar(user.getAvatar());
        return author;
    }

    private List<String> visibleRoles() {
        return EnumSet.of(USER, ASSISTANT, SYSTEM)
                .stream()
                .map(Enum::name)
                .toList();
    }

    private List<String> visibleMessageTypes() {
        return EnumSet.of(CHAT, ERROR)
                .stream()
                .map(Enum::name)
                .toList();
    }
}
