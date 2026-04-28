package org.ymz.app.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ymz.app.converter.AppConverter;
import org.ymz.app.model.dto.app.AppChatMessageInfo;
import org.ymz.app.model.dto.app.AppDetail;
import org.ymz.app.model.dto.app.AppSummary;
import org.ymz.app.model.dto.app.AppTaskInfo;
import org.ymz.app.model.dto.app.ListAppMessagesRequest;
import org.ymz.app.model.dto.app.ListAppTasksRequest;
import org.ymz.app.model.dto.app.ListAppsRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppDeployStatus;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.service.AppChatMessageService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AppQueryServiceImplTest {

    @Test
    void listAppsFiltersCurrentUserAndMapsPage() {
        Fixture fixture = fixture();
        ListAppsRequest request = new ListAppsRequest();
        request.setKeyword("dashboard");
        request.setStatus(AppStatus.READY.name());
        request.setDeployStatus(AppDeployStatus.DEPLOYED.name());

        App app = App.builder()
                .id(1L)
                .userId(10L)
                .name("dashboard")
                .status(AppStatus.READY.name())
                .createdAt(LocalDateTime.now())
                .build();
        when(fixture.appService.page(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(new Page<>(List.of(app), 1, 10, 1));

        PageResult<AppSummary> result = fixture.service.listApps(10L, request);

        assertEquals(1, result.getTotal());
        assertEquals(1L, result.getList().getFirst().getId());
        QueryWrapper query = captureQuery(fixture.appService);
        String sql = query.toSQL().toLowerCase();
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("ready"));
        assertTrue(sql.contains("deployed"));
        assertTrue(sql.contains("dashboard"));
        assertTrue(sql.contains("created_at") && sql.contains("desc"));
    }

    @Test
    void getAppReturnsOwnedApp() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(ownedApp());

        AppDetail detail = fixture.service.getApp(10L, 1L);

        assertEquals(1L, detail.getId());
        assertEquals("prompt", detail.getInitPrompt());
    }

    @Test
    void getAppRejectsMissingApp() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> fixture.service.getApp(10L, 1L));

        assertEquals(ResultCode.NOT_FOUND, exception.getResultCode());
    }

    @Test
    void getAppRejectsOtherUserApp() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(App.builder().id(1L).userId(99L).build());

        BusinessException exception = assertThrows(BusinessException.class, () -> fixture.service.getApp(10L, 1L));

        assertEquals(ResultCode.NO_PERMISSION, exception.getResultCode());
    }

    @Test
    void listAppTasksRequiresOwnedAppAndOrdersNewestFirst() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(ownedApp());
        AppTask task = AppTask.builder()
                .id(2L)
                .appId(1L)
                .userId(10L)
                .taskType(AppTaskType.CREATE.name())
                .status(AppTaskStatus.SUCCESS.name())
                .build();
        when(fixture.appTaskService.page(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(new Page<>(List.of(task), 1, 10, 1));

        PageResult<AppTaskInfo> result = fixture.service.listAppTasks(10L, 1L, new ListAppTasksRequest());

        assertEquals(2L, result.getList().getFirst().getId());
        QueryWrapper query = captureQuery(fixture.appTaskService);
        String sql = query.toSQL().toLowerCase();
        assertTrue(sql.contains("app_id"));
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("created_at") && sql.contains("desc"));
    }

    @Test
    void listAppMessagesCanFilterTaskAndOrdersChronologically() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(ownedApp());
        when(fixture.appTaskService.getById(2L)).thenReturn(ownedTask(1L));
        AppChatMessage message = AppChatMessage.builder()
                .id(3L)
                .appId(1L)
                .taskId(2L)
                .content("done")
                .build();
        when(fixture.appChatMessageService.page(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(new Page<>(List.of(message), 1, 10, 1));

        ListAppMessagesRequest request = new ListAppMessagesRequest();
        request.setTaskId(2L);
        PageResult<AppChatMessageInfo> result = fixture.service.listAppMessages(10L, 1L, request);

        assertEquals(3L, result.getList().getFirst().getId());
        QueryWrapper query = captureQuery(fixture.appChatMessageService);
        String sql = query.toSQL().toLowerCase();
        assertTrue(sql.contains("app_id"));
        assertTrue(sql.contains("task_id"));
        assertTrue(sql.contains("created_at") && sql.contains("asc"));
    }

    @Test
    void listAppMessagesRejectsTaskFromOtherApp() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(ownedApp());
        when(fixture.appTaskService.getById(2L)).thenReturn(ownedTask(99L));
        ListAppMessagesRequest request = new ListAppMessagesRequest();
        request.setTaskId(2L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.listAppMessages(10L, 1L, request)
        );

        assertEquals(ResultCode.NOT_FOUND, exception.getResultCode());
        verify(fixture.appChatMessageService, never()).page(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void getTaskRejectsOtherUserTask() {
        Fixture fixture = fixture();
        when(fixture.appTaskService.getById(2L)).thenReturn(AppTask.builder().id(2L).userId(99L).build());

        BusinessException exception = assertThrows(BusinessException.class, () -> fixture.service.getTask(10L, 2L));

        assertEquals(ResultCode.NO_PERMISSION, exception.getResultCode());
    }

    private Fixture fixture() {
        AppService appService = mock(AppService.class);
        AppTaskService appTaskService = mock(AppTaskService.class);
        AppChatMessageService appChatMessageService = mock(AppChatMessageService.class);
        AppQueryServiceImpl service = new AppQueryServiceImpl(
                appService,
                appTaskService,
                appChatMessageService,
                new FakeAppConverter()
        );
        return new Fixture(service, appService, appTaskService, appChatMessageService);
    }

    private App ownedApp() {
        return App.builder()
                .id(1L)
                .userId(10L)
                .name("app")
                .initPrompt("prompt")
                .status(AppStatus.READY.name())
                .build();
    }

    private AppTask ownedTask(Long appId) {
        return AppTask.builder()
                .id(2L)
                .appId(appId)
                .userId(10L)
                .taskType(AppTaskType.CREATE.name())
                .build();
    }

    private QueryWrapper captureQuery(AppService appService) {
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(appService).page(any(Page.class), queryCaptor.capture());
        return queryCaptor.getValue();
    }

    private QueryWrapper captureQuery(AppTaskService appTaskService) {
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(appTaskService).page(any(Page.class), queryCaptor.capture());
        return queryCaptor.getValue();
    }

    private QueryWrapper captureQuery(AppChatMessageService appChatMessageService) {
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(appChatMessageService).page(any(Page.class), queryCaptor.capture());
        return queryCaptor.getValue();
    }

    private record Fixture(
            AppQueryServiceImpl service,
            AppService appService,
            AppTaskService appTaskService,
            AppChatMessageService appChatMessageService
    ) {
    }

    private static class FakeAppConverter implements AppConverter {

        @Override
        public AppSummary toAppSummary(App app) {
            AppSummary summary = new AppSummary();
            summary.setId(app.getId());
            summary.setName(app.getName());
            summary.setStatus(app.getStatus());
            return summary;
        }

        @Override
        public AppDetail toAppDetail(App app) {
            AppDetail detail = new AppDetail();
            detail.setId(app.getId());
            detail.setName(app.getName());
            detail.setStatus(app.getStatus());
            detail.setInitPrompt(app.getInitPrompt());
            return detail;
        }

        @Override
        public AppTaskInfo toAppTaskInfo(AppTask task) {
            AppTaskInfo info = new AppTaskInfo();
            info.setId(task.getId());
            info.setAppId(task.getAppId());
            info.setTaskType(task.getTaskType());
            info.setStatus(task.getStatus());
            return info;
        }

        @Override
        public AppChatMessageInfo toAppChatMessageInfo(AppChatMessage message) {
            AppChatMessageInfo info = new AppChatMessageInfo();
            info.setId(message.getId());
            info.setAppId(message.getAppId());
            info.setTaskId(message.getTaskId());
            info.setContent(message.getContent());
            return info;
        }
    }
}
