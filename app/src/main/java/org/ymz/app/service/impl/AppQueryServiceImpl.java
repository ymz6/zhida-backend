package org.ymz.app.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.Brackets;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.AppConverter;
import org.ymz.app.model.dto.app.AppChatMessageInfo;
import org.ymz.app.model.dto.app.AppDetail;
import org.ymz.app.model.dto.app.AppSummary;
import org.ymz.app.model.dto.app.AppTaskInfo;
import org.ymz.app.model.dto.app.ListAppMessagesRequest;
import org.ymz.app.model.dto.app.ListAppTasksRequest;
import org.ymz.app.model.dto.app.ListAppsRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.dto.page.SortablePageQuery;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.service.AppChatMessageService;
import org.ymz.app.service.AppQueryService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import static org.ymz.app.model.entity.table.AppChatMessageTableDef.APP_CHAT_MESSAGE;
import static org.ymz.app.model.entity.table.AppTableDef.APP;
import static org.ymz.app.model.entity.table.AppTaskTableDef.APP_TASK;

/**
 * 应用生成前端查询服务实现。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppQueryServiceImpl implements AppQueryService {

    private final AppService appService;
    private final AppTaskService appTaskService;
    private final AppChatMessageService appChatMessageService;
    private final AppConverter appConverter;

    @Override
    public PageResult<AppSummary> listApps(Long userId, ListAppsRequest request) {
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
        return PageResult.of(page, appConverter::toAppSummary);
    }

    @Override
    public AppDetail getApp(Long userId, Long appId) {
        return appConverter.toAppDetail(getOwnedApp(userId, appId));
    }

    @Override
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

    @Override
    public PageResult<AppChatMessageInfo> listAppMessages(
            Long userId,
            Long appId,
            ListAppMessagesRequest request
    ) {
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
                .and(APP_CHAT_MESSAGE.TASK_ID.eq(taskId, If::notNull))
                .orderBy(APP_CHAT_MESSAGE.CREATED_AT.asc())
                .orderBy(APP_CHAT_MESSAGE.ID.asc());

        Page<AppChatMessage> page = appChatMessageService.page(request.toPage(), query);
        return PageResult.of(page, appConverter::toAppChatMessageInfo);
    }

    @Override
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
}
