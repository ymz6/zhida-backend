package org.ymz.app.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ymz.app.converter.AppConverter;
import org.ymz.app.model.dto.app.AppChatMessageInfo;
import org.ymz.app.model.dto.app.AppDetail;
import org.ymz.app.model.dto.app.AppSummary;
import org.ymz.app.model.dto.app.ListAppMessagesRequest;
import org.ymz.app.model.dto.app.ListMyAppsRequest;
import org.ymz.app.model.dto.page.CursorResult;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.enums.app.AppDeployStatus;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.service.AppChatMessageService;
import org.ymz.app.service.AppQueryService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.UserService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        QueryWrapper query = captureAppQuery(fixture.appService);
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
        QueryWrapper query = captureMessageQuery(fixture.appChatMessageService);
        String sql = query.toSQL().toLowerCase();
        assertTrue(sql.contains("app_id"));
        assertTrue(sql.contains("id") && sql.contains("desc"));
        assertTrue(sql.contains("limit"));
    }

    @Test
    void listAppMessagesLoadsBeforeCursor() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(ownedApp());

        ListAppMessagesRequest request = new ListAppMessagesRequest();
        request.setLimit(2);
        request.setBefore(4L);
        when(fixture.appChatMessageService.list(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        message(3L, null, "ASSISTANT", "CHAT"),
                        message(2L, null, "USER", "CHAT")
                ));

        CursorResult<AppChatMessageInfo> result = fixture.service.listAppMessages(10L, 1L, request);

        assertEquals(List.of(2L, 3L), result.getList().stream().map(AppChatMessageInfo::getId).toList());
        QueryWrapper query = captureMessageQuery(fixture.appChatMessageService);
        String sql = query.toSQL().toLowerCase();
        assertTrue(sql.contains("app_id"));
        assertTrue(sql.contains("id") && sql.contains("<") && sql.contains("4"));
    }

    private Fixture fixture() {
        AppService appService = mock(AppService.class);
        AppChatMessageService appChatMessageService = mock(AppChatMessageService.class);
        UserService userService = mock(UserService.class);
        AppQueryService service = new AppQueryService(
                appService,
                appChatMessageService,
                userService,
                new FakeAppConverter()
        );
        return new Fixture(service, appService, appChatMessageService, userService);
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

    private QueryWrapper captureAppQuery(AppService appService) {
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        org.mockito.Mockito.verify(appService).page(any(Page.class), queryCaptor.capture());
        return queryCaptor.getValue();
    }

    private QueryWrapper captureMessageQuery(AppChatMessageService appChatMessageService) {
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        org.mockito.Mockito.verify(appChatMessageService).list(queryCaptor.capture());
        return queryCaptor.getValue();
    }

    private record Fixture(
            AppQueryService service,
            AppService appService,
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
    }
}
