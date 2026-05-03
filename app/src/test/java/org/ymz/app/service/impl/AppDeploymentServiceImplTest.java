package org.ymz.app.service.impl;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ymz.app.config.AppDeploymentProperties;
import org.ymz.app.model.dto.app.DeployAppResponse;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppDeployStatus;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.service.AppCoverCaptureService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.service.deployment.AppDeploymentFilePublisher;
import org.ymz.app.web.exception.BusinessException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppDeploymentServiceImplTest {

    private static final Pattern DEPLOY_KEY_PATTERN = Pattern.compile("[0-9a-f]{32}");

    @TempDir
    Path tempDir;

    @Test
    void deployReadyAppCreatesDeployKeyAndUrl() throws Exception {
        Fixture fixture = fixture(app(null, AppStatus.READY, workspaceWithDist()));

        DeployAppResponse response = fixture.service.deployApp(10L, 1L);

        assertEquals(1L, response.getAppId());
        assertEquals(AppDeployStatus.DEPLOYED.name(), response.getDeployStatus());
        assertNotNull(response.getDeployedAt());

        ArgumentCaptor<String> deployKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.filePublisher).publish(eq(tempDir.resolve("workspace/dist").toAbsolutePath().normalize()), deployKeyCaptor.capture());
        String deployKey = deployKeyCaptor.getValue();
        assertTrue(DEPLOY_KEY_PATTERN.matcher(deployKey).matches());
        assertEquals("http://localhost/apps/" + deployKey + "/", response.getDeployUrl());

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        App successUpdate = appCaptor.getAllValues().getLast();
        assertEquals(AppDeployStatus.DEPLOYED.name(), successUpdate.getDeployStatus());
        assertEquals(deployKey, successUpdate.getDeployKey());
        assertEquals("http://localhost/apps/" + deployKey + "/", successUpdate.getDeployUrl());
        assertEquals(tempDir.resolve("deployments").resolve(deployKey).toString(), successUpdate.getDeployPath());
        assertEquals("", successUpdate.getDeployErrorMessage());

        ArgumentCaptor<LocalDateTime> deployedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fixture.appCoverCaptureService).captureCoverAsync(
                eq(1L),
                eq(response.getDeployUrl()),
                deployedAtCaptor.capture()
        );
        assertEquals(response.getDeployedAt(), deployedAtCaptor.getValue());

        ArgumentCaptor<AppTask> savedTaskCaptor = ArgumentCaptor.forClass(AppTask.class);
        verify(fixture.appTaskService).save(savedTaskCaptor.capture());
        AppTask savedTask = savedTaskCaptor.getValue();
        assertEquals(AppTaskType.DEPLOY.name(), savedTask.getTaskType());
        assertEquals(AppTaskStatus.RUNNING.name(), savedTask.getStatus());
        assertEquals("部署应用", savedTask.getPrompt());

        ArgumentCaptor<AppTask> updatedTaskCaptor = ArgumentCaptor.forClass(AppTask.class);
        verify(fixture.appTaskService).updateById(updatedTaskCaptor.capture());
        AppTask taskUpdate = updatedTaskCaptor.getValue();
        assertEquals(30L, taskUpdate.getId());
        assertEquals(AppTaskStatus.SUCCESS.name(), taskUpdate.getStatus());
        assertEquals("应用部署成功", taskUpdate.getResultSummary());
        verify(fixture.appTaskMetrics).recordCreated(AppTaskType.DEPLOY);
        verify(fixture.appTaskMetrics).recordStarted(savedTask);
        verify(fixture.appTaskMetrics).recordCompleted(eq(savedTask), eq(AppTaskStatus.SUCCESS), any(LocalDateTime.class));
    }

    @Test
    void redeployReusesExistingDeployKey() throws Exception {
        App app = app("fixed-key", AppStatus.READY, workspaceWithDist());
        Fixture fixture = fixture(app);

        DeployAppResponse response = fixture.service.deployApp(10L, 1L);

        verify(fixture.filePublisher).publish(any(Path.class), eq("fixed-key"));
        assertEquals("http://localhost/apps/fixed-key/", response.getDeployUrl());

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        assertEquals("fixed-key", appCaptor.getAllValues().getLast().getDeployKey());
    }

    @Test
    void deployStillSucceedsWhenCoverCaptureSubmissionFails() throws Exception {
        Fixture fixture = fixture(app(null, AppStatus.READY, workspaceWithDist()));
        doThrow(new RuntimeException("queue full")).when(fixture.appCoverCaptureService)
                .captureCoverAsync(any(Long.class), anyString(), any(LocalDateTime.class));

        DeployAppResponse response = fixture.service.deployApp(10L, 1L);

        assertEquals(AppDeployStatus.DEPLOYED.name(), response.getDeployStatus());
    }

    @Test
    void rejectsOtherUsersApp() throws Exception {
        Fixture fixture = fixture(App.builder()
                .id(1L)
                .userId(99L)
                .status(AppStatus.READY.name())
                .workspacePath(workspaceWithDist().toString())
                .build());

        assertThrows(BusinessException.class, () -> fixture.service.deployApp(10L, 1L));

        verify(fixture.appService, never()).updateChain();
        verify(fixture.filePublisher, never()).publish(any(Path.class), anyString());
    }

    @Test
    void rejectsMissingApp() {
        Fixture fixture = fixture(null);

        assertThrows(BusinessException.class, () -> fixture.service.deployApp(10L, 1L));

        verify(fixture.appService, never()).updateChain();
    }

    @Test
    void rejectsNonReadyApp() throws Exception {
        Fixture fixture = fixture(app(null, AppStatus.BUILDING, workspaceWithDist()));

        assertThrows(BusinessException.class, () -> fixture.service.deployApp(10L, 1L));

        verify(fixture.appService, never()).updateChain();
    }

    @Test
    void rejectsAppWithActiveGenerationTask() throws Exception {
        Fixture fixture = fixture(app(null, AppStatus.READY, workspaceWithDist()));
        when(fixture.appTaskService.count(any(QueryWrapper.class))).thenReturn(1L);

        assertThrows(BusinessException.class, () -> fixture.service.deployApp(10L, 1L));

        verify(fixture.appService, never()).updateChain();
    }

    @Test
    void deployFailureRecordsErrorWithoutChangingAppStatus() throws Exception {
        Path workspacePath = tempDir.resolve("workspace");
        Files.createDirectories(workspacePath);
        Fixture fixture = fixture(app(null, AppStatus.READY, workspacePath));

        assertThrows(BusinessException.class, () -> fixture.service.deployApp(10L, 1L));

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(fixture.appService, atLeastOnce()).updateById(appCaptor.capture());
        App failureUpdate = appCaptor.getAllValues().getLast();
        assertEquals(AppDeployStatus.FAILED.name(), failureUpdate.getDeployStatus());
        assertTrue(failureUpdate.getDeployErrorMessage().contains("构建产物不存在"));
        assertEquals(null, failureUpdate.getStatus());
        assertEquals(null, failureUpdate.getPreviewUrl());
        assertEquals(null, failureUpdate.getErrorMessage());
        verify(fixture.filePublisher, never()).publish(any(Path.class), anyString());
        verify(fixture.appCoverCaptureService, never()).captureCoverAsync(any(Long.class), anyString(), any(LocalDateTime.class));

        ArgumentCaptor<AppTask> updatedTaskCaptor = ArgumentCaptor.forClass(AppTask.class);
        verify(fixture.appTaskService).updateById(updatedTaskCaptor.capture());
        AppTask taskUpdate = updatedTaskCaptor.getValue();
        assertEquals(AppTaskStatus.FAILED.name(), taskUpdate.getStatus());
        assertTrue(taskUpdate.getErrorMessage().contains("构建产物不存在"));
        verify(fixture.appTaskMetrics).recordCompleted(any(AppTask.class), eq(AppTaskStatus.FAILED), any(LocalDateTime.class));
    }

    @Test
    void rejectsWhenAnotherDeploymentIsRunning() throws Exception {
        Fixture fixture = fixture(app(null, AppStatus.READY, workspaceWithDist()));
        when(fixture.updateChain.update()).thenReturn(false);

        assertThrows(BusinessException.class, () -> fixture.service.deployApp(10L, 1L));

        verify(fixture.filePublisher, never()).publish(any(Path.class), anyString());
    }

    private Fixture fixture(App app) {
        AppService appService = mock(AppService.class);
        AppTaskService appTaskService = mock(AppTaskService.class);
        AppDeploymentFilePublisher filePublisher = mock(AppDeploymentFilePublisher.class);
        AppCoverCaptureService appCoverCaptureService = mock(AppCoverCaptureService.class);
        AppTaskMetrics appTaskMetrics = mock(AppTaskMetrics.class);
        @SuppressWarnings("unchecked")
        UpdateChain<App> updateChain = mock(UpdateChain.class, RETURNS_SELF);
        AppDeploymentProperties properties = new AppDeploymentProperties();
        properties.setDeployRoot(tempDir.resolve("deployments").toString());
        properties.setDeployUrlPrefix("http://localhost/apps");

        when(appService.getById(1L)).thenReturn(app);
        when(appTaskService.count(any(QueryWrapper.class))).thenReturn(0L);
        when(appService.updateChain()).thenReturn(updateChain);
        when(updateChain.set(any(QueryColumn.class), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        when(appTaskService.save(any(AppTask.class))).thenAnswer(invocation -> {
            AppTask task = invocation.getArgument(0);
            task.setId(30L);
            return true;
        });
        try {
            when(filePublisher.publish(any(Path.class), anyString()))
                    .thenAnswer(invocation -> tempDir.resolve("deployments").resolve((String) invocation.getArgument(1)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        AppDeploymentServiceImpl service = new AppDeploymentServiceImpl(
                appService,
                appTaskService,
                filePublisher,
                properties,
                appCoverCaptureService,
                appTaskMetrics
        );
        return new Fixture(
                service,
                appService,
                appTaskService,
                filePublisher,
                appCoverCaptureService,
                appTaskMetrics,
                updateChain
        );
    }

    private App app(String deployKey, AppStatus status, Path workspacePath) {
        return App.builder()
                .id(1L)
                .userId(10L)
                .status(status.name())
                .deployKey(deployKey)
                .workspacePath(workspacePath.toString())
                .build();
    }

    private Path workspaceWithDist() throws Exception {
        Path workspacePath = tempDir.resolve("workspace");
        Files.createDirectories(workspacePath.resolve("dist/assets"));
        Files.writeString(workspacePath.resolve("dist/index.html"), "<html></html>");
        Files.writeString(workspacePath.resolve("dist/assets/app.js"), "console.log('ok')");
        return workspacePath;
    }

    private record Fixture(
            AppDeploymentServiceImpl service,
            AppService appService,
            AppTaskService appTaskService,
            AppDeploymentFilePublisher filePublisher,
            AppCoverCaptureService appCoverCaptureService,
            AppTaskMetrics appTaskMetrics,
            UpdateChain<App> updateChain
    ) {
    }
}
