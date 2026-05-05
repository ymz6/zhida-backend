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
import org.ymz.app.model.dto.app.ListMyAppsRequest;
import org.ymz.app.model.dto.page.CursorResult;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.dto.task.AppTaskEventInfo;
import org.ymz.app.model.dto.task.ListTaskEventsRequest;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTaskEvent;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.enums.app.AppDeployStatus;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.service.AppChatMessageService;
import org.ymz.app.service.AppQueryService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskEventService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.service.UserService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AppQueryServiceTest {

    @Test
    void listMyAppsFiltersCurrentUserAndMapsPage() {
        Fixture fixture = fixture();
        ListMyAppsRequest request = new ListMyAppsRequest();
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
        when(fixture.userService.getById(10L)).thenReturn(author());

        PageResult<AppSummary> result = fixture.service.listMyApps(10L, request);

        assertEquals(1, result.getTotal());
        assertEquals(1L, result.getList().getFirst().getId());
        assertEquals(10L, result.getList().getFirst().getAuthor().getId());
        assertEquals("作者", result.getList().getFirst().getAuthor().getNickname());
        assertEquals("https://example.com/avatar.png", result.getList().getFirst().getAuthor().getAvatar());
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
        when(fixture.userService.getById(10L)).thenReturn(author());

        AppDetail detail = fixture.service.getApp(10L, 1L);

        assertEquals(1L, detail.getId());
        assertEquals("prompt", detail.getInitPrompt());
        assertEquals(10L, detail.getAuthor().getId());
        assertEquals("作者", detail.getAuthor().getNickname());
        assertEquals("https://example.com/avatar.png", detail.getAuthor().getAvatar());
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
    void listAppMessagesReturnsLatestLimitChronologically() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(ownedApp());

        ListAppMessagesRequest request = new ListAppMessagesRequest();
        request.setLimit(2);
        when(fixture.appChatMessageService.list(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        message(5L, null, "ASSISTANT", "CHAT"),
                        message(4L, null, "SYSTEM", "CHAT"),
                        message(3L, null, "USER", "CHAT")
                ));

        CursorResult<AppChatMessageInfo> result = fixture.service.listAppMessages(10L, 1L, request);

        assertEquals(List.of(4L, 5L), result.getList().stream().map(AppChatMessageInfo::getId).toList());
        assertTrue(result.isHasMore());
        assertEquals(4L, result.getNextCursor());
        QueryWrapper query = captureQuery(fixture.appChatMessageService);
        String sql = query.toSQL().toLowerCase();
        assertTrue(sql.contains("app_id"));
        assertTrue(sql.contains("id") && sql.contains("desc"));
        assertTrue(sql.contains("limit"));
    }

    @Test
    void listAppMessagesLoadsBeforeCursorAndCanFilterTask() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(ownedApp());
        when(fixture.appTaskService.getById(2L)).thenReturn(ownedTask(1L));

        ListAppMessagesRequest request = new ListAppMessagesRequest();
        request.setTaskId(2L);
        request.setLimit(2);
        request.setBefore(4L);
        when(fixture.appChatMessageService.list(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        message(3L, 2L, "ASSISTANT", "CHAT"),
                        message(2L, 2L, "USER", "CHAT")
                ));

        CursorResult<AppChatMessageInfo> result = fixture.service.listAppMessages(10L, 1L, request);

        assertEquals(List.of(2L, 3L), result.getList().stream().map(AppChatMessageInfo::getId).toList());
        assertFalse(result.isHasMore());
        assertNull(result.getNextCursor());
        QueryWrapper query = captureQuery(fixture.appChatMessageService);
        String sql = query.toSQL().toLowerCase();
        assertTrue(sql.contains("app_id"));
        assertTrue(sql.contains("task_id"));
        assertTrue(sql.contains("id") && sql.contains("<") && sql.contains("4"));
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
        verify(fixture.appChatMessageService, never()).list(any(QueryWrapper.class));
    }

    @Test
    void listTaskEventsReturnsLatestLimitChronologically() {
        Fixture fixture = fixture();
        when(fixture.appTaskService.getById(2L)).thenReturn(ownedTask(1L));

        ListTaskEventsRequest request = new ListTaskEventsRequest();
        request.setLimit(2);
        when(fixture.appTaskEventService.list(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        event(5L, "agent.run.finished"),
                        event(4L, "agent.validation.started"),
                        event(3L, "agent.run.started")
                ));

        CursorResult<AppTaskEventInfo> result = fixture.service.listTaskEvents(10L, 2L, request);

        assertEquals(List.of(4L, 5L), result.getList().stream().map(AppTaskEventInfo::getId).toList());
        assertTrue(result.isHasMore());
        assertEquals(4L, result.getNextCursor());
        QueryWrapper query = captureEventQuery(fixture.appTaskEventService);
        String sql = query.toSQL().toLowerCase();
        assertTrue(sql.contains("task_id"));
        assertTrue(sql.contains("limit"));
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
        AppTaskEventService appTaskEventService = mock(AppTaskEventService.class);
        AppChatMessageService appChatMessageService = mock(AppChatMessageService.class);
        UserService userService = mock(UserService.class);
        AppQueryService service = new AppQueryService(
                appService,
                appTaskService,
                appTaskEventService,
                appChatMessageService,
                userService,
                new FakeAppConverter()
        );
        return new Fixture(service, appService, appTaskService, appTaskEventService, appChatMessageService, userService);
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

    private User author() {
        return User.builder()
                .id(10L)
                .nickname("作者")
                .avatar("https://example.com/avatar.png")
                .build();
    }

    private AppChatMessage message(Long id, Long taskId, String role, String messageType) {
        return AppChatMessage.builder()
                .id(id)
                .appId(1L)
                .taskId(taskId)
                .role(role)
                .messageType(messageType)
                .content("msg-" + id)
                .build();
    }

    private AppTaskEvent event(Long id, String eventType) {
        return AppTaskEvent.builder()
                .id(id)
                .appId(1L)
                .taskId(2L)
                .eventType(eventType)
                .content("event-" + id)
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
        verify(appChatMessageService).list(queryCaptor.capture());
        return queryCaptor.getValue();
    }

    private QueryWrapper captureEventQuery(AppTaskEventService appTaskEventService) {
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(appTaskEventService).list(queryCaptor.capture());
        return queryCaptor.getValue();
    }

    private record Fixture(
            AppQueryService service,
            AppService appService,
            AppTaskService appTaskService,
            AppTaskEventService appTaskEventService,
            AppChatMessageService appChatMessageService,
            UserService userService
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
            info.setRole(message.getRole());
            info.setMessageType(message.getMessageType());
            info.setContent(message.getContent());
            return info;
        }

        @Override
        public AppTaskEventInfo toAppTaskEventInfo(AppTaskEvent event) {
            AppTaskEventInfo info = new AppTaskEventInfo();
            info.setId(event.getId());
            info.setAppId(event.getAppId());
            info.setTaskId(event.getTaskId());
            info.setEventType(event.getEventType());
            info.setContent(event.getContent());
            info.setMetadata(event.getMetadata());
            info.setCreatedAt(event.getCreatedAt());
            return info;
        }
    }
}
