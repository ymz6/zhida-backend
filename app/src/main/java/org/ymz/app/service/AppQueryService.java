package org.ymz.app.service;

import org.ymz.app.model.dto.app.AppChatMessageInfo;
import org.ymz.app.model.dto.app.AppDetail;
import org.ymz.app.model.dto.app.AppSummary;
import org.ymz.app.model.dto.app.AppTaskInfo;
import org.ymz.app.model.dto.app.ListAppMessagesRequest;
import org.ymz.app.model.dto.app.ListAppTasksRequest;
import org.ymz.app.model.dto.app.ListAppsRequest;
import org.ymz.app.model.dto.page.PageResult;

/**
 * 应用生成前端查询服务。
 *
 * @author ymz
 */
public interface AppQueryService {

    PageResult<AppSummary> listApps(Long userId, ListAppsRequest request);

    AppDetail getApp(Long userId, Long appId);

    PageResult<AppTaskInfo> listAppTasks(Long userId, Long appId, ListAppTasksRequest request);

    PageResult<AppChatMessageInfo> listAppMessages(Long userId, Long appId, ListAppMessagesRequest request);

    AppTaskInfo getTask(Long userId, Long taskId);
}
