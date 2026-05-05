package org.ymz.app.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ymz.app.ai.title.TitleGenerateAssistant;
import org.ymz.app.config.AppDeploymentProperties;
import org.ymz.app.deployment.AppCoverCaptureService;
import org.ymz.app.deployment.AppDeploymentFileService;
import org.ymz.app.model.dto.app.CreateAppIterationRequest;
import org.ymz.app.model.dto.app.CreateAppTaskResponse;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.service.AppOperationService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.ai.codegen.event.CodeGenerationMessageRecorder;
import org.ymz.app.web.exception.BusinessException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppOperationIterationTest {

    @TempDir
    Path workspacePath;

    @Test
    void readyAppCanCreateIterationTask() throws Exception {
        Fixture fixture = fixture(AppStatus.READY, workspacePath);
        CreateAppIterationRequest request = request("加一个筛选面板");

        CreateAppTaskResponse response = fixture.service.createAppIteration(10L, 1L, request);

        assertEquals(1L, response.getAppId());
        assertEquals(20L, response.getTaskId());
        assertEquals("测试应用", response.getName());
        assertEquals(AppStatus.READY.name(), response.getStatus());

        ArgumentCaptor<AppTask> taskCaptor = ArgumentCaptor.forClass(AppTask.class);
        verify(fixture.appTaskService).save(taskCaptor.capture());
        AppTask task = taskCaptor.getValue();
        assertEquals(AppTaskType.ITERATE.name(), task.getTaskType());
        assertEquals(AppTaskStatus.PENDING.name(), task.getStatus());
        assertEquals("加一个筛选面板", task.getPrompt());

        verify(fixture.appService).updateById(fixture.app);
        assertEquals(20L, fixture.app.getLatestTaskId());
        verify(fixture.appTaskLogPublisher).appendMessage(
                eq(1L),
                eq(20L),
                eq(AppChatMessageRole.USER),
                eq(AppChatMessageType.CHAT),
                eq("加一个筛选面板")
        );
    }

    @Test
    void failedAppWithWorkspaceCanCreateIterationTask() throws Exception {
        Fixture fixture = fixture(AppStatus.FAILED, workspacePath);

        CreateAppTaskResponse response = fixture.service.createAppIteration(10L, 1L, request("继续修复页面"));

        assertEquals(20L, response.getTaskId());
        verify(fixture.appTaskService).save(any(AppTask.class));
    }

    @Test
    void rejectsOtherUsersApp() throws Exception {
        Fixture fixture = fixture(AppStatus.READY, workspacePath);

        assertThrows(BusinessException.class,
                () -> fixture.service.createAppIteration(99L, 1L, request("继续修复页面")));

        verify(fixture.appTaskService, never()).save(any(AppTask.class));
    }

    @Test
    void rejectsAppWithoutWorkspace() throws Exception {
        Fixture fixture = fixture(AppStatus.READY, null);

        assertThrows(BusinessException.class,
                () -> fixture.service.createAppIteration(10L, 1L, request("继续修复页面")));

        verify(fixture.appTaskService, never()).save(any(AppTask.class));
    }

    @Test
    void rejectsWhenAppHasActiveTask() throws Exception {
        Fixture fixture = fixture(AppStatus.READY, workspacePath);
        when(fixture.appTaskService.count(any(QueryWrapper.class))).thenReturn(1L);

        assertThrows(BusinessException.class,
                () -> fixture.service.createAppIteration(10L, 1L, request("继续修复页面")));

        verify(fixture.appTaskService, never()).save(any(AppTask.class));
    }

    private Fixture fixture(AppStatus status, Path workspacePath) throws Exception {
        if (workspacePath != null) {
            Files.createDirectories(workspacePath);
        }
        AppService appService = mock(AppService.class);
        AppTaskService appTaskService = mock(AppTaskService.class);
        CodeGenerationMessageRecorder appTaskLogPublisher = mock(CodeGenerationMessageRecorder.class);
        AppTaskMetrics appTaskMetrics = mock(AppTaskMetrics.class);
        App app = App.builder()
                .id(1L)
                .userId(10L)
                .name("测试应用")
                .status(status.name())
                .workspacePath(workspacePath == null ? null : workspacePath.toString())
                .build();
        when(appService.getById(1L)).thenReturn(app);
        when(appTaskService.count(any(QueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            AppTask task = invocation.getArgument(0);
            task.setId(20L);
            return true;
        }).when(appTaskService).save(any(AppTask.class));
        AppOperationService service = new AppOperationService(
                mock(TitleGenerateAssistant.class),
                appService,
                appTaskService,
                appTaskLogPublisher,
                mock(AppDeploymentFileService.class),
                new AppDeploymentProperties(),
                mock(AppCoverCaptureService.class),
                appTaskMetrics
        );
        return new Fixture(service, appService, appTaskService, appTaskLogPublisher, app);
    }

    private CreateAppIterationRequest request(String prompt) {
        CreateAppIterationRequest request = new CreateAppIterationRequest();
        request.setPrompt(prompt);
        return request;
    }

    private record Fixture(
            AppOperationService service,
            AppService appService,
            AppTaskService appTaskService,
            CodeGenerationMessageRecorder appTaskLogPublisher,
            App app
    ) {
    }
}
