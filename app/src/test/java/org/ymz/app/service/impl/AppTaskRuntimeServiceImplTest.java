package org.ymz.app.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.update.UpdateChain;
import org.junit.jupiter.api.Test;
import org.ymz.app.model.dto.task.TaskStatusResponse;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskStep;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.service.generation.AppCreateTaskRunner;
import org.ymz.app.service.generation.AppIterationTaskRunner;
import org.ymz.app.service.generation.AppTaskLogPublisher;
import org.ymz.app.service.generation.AppTaskSseBroker;
import org.ymz.app.web.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.ymz.app.model.entity.table.AppTaskTableDef.APP_TASK;

class AppTaskRuntimeServiceImplTest {

    @Test
    void startTaskDispatchesCreateTask() {
        Fixture fixture = fixture(task(AppTaskType.CREATE, AppTaskStatus.PENDING), AppTaskStep.INITIALIZING_WORKSPACE);

        TaskStatusResponse response = fixture.service.startTask(10L, 2L);

        assertEquals(AppTaskStep.INITIALIZING_WORKSPACE.name(), response.getCurrentStep());
        verify(fixture.updateChain).set(eq(APP_TASK.CURRENT_STEP), eq(AppTaskStep.INITIALIZING_WORKSPACE.name()));
        verify(fixture.appCreateTaskRunner).runCreateTask(2L);
        verify(fixture.appIterationTaskRunner, never()).runIterationTask(2L);
    }

    @Test
    void startTaskDispatchesIterateTask() {
        Fixture fixture = fixture(task(AppTaskType.ITERATE, AppTaskStatus.PENDING), AppTaskStep.GENERATING_CODE);

        TaskStatusResponse response = fixture.service.startTask(10L, 2L);

        assertEquals(AppTaskStep.GENERATING_CODE.name(), response.getCurrentStep());
        verify(fixture.updateChain).set(eq(APP_TASK.CURRENT_STEP), eq(AppTaskStep.GENERATING_CODE.name()));
        verify(fixture.appIterationTaskRunner).runIterationTask(2L);
        verify(fixture.appCreateTaskRunner, never()).runCreateTask(2L);
    }

    @Test
    void startTaskRejectsUnsupportedTaskType() {
        Fixture fixture = fixture(task(AppTaskType.DEPLOY, AppTaskStatus.PENDING), AppTaskStep.DEPLOYING);

        assertThrows(BusinessException.class, () -> fixture.service.startTask(10L, 2L));

        verify(fixture.appCreateTaskRunner, never()).runCreateTask(2L);
        verify(fixture.appIterationTaskRunner, never()).runIterationTask(2L);
    }

    private Fixture fixture(AppTask task, AppTaskStep startedStep) {
        AppTaskService appTaskService = mock(AppTaskService.class);
        AppTaskLogPublisher appTaskLogPublisher = mock(AppTaskLogPublisher.class);
        AppTaskSseBroker appTaskSseBroker = mock(AppTaskSseBroker.class);
        AppCreateTaskRunner appCreateTaskRunner = mock(AppCreateTaskRunner.class);
        AppIterationTaskRunner appIterationTaskRunner = mock(AppIterationTaskRunner.class);
        @SuppressWarnings("unchecked")
        UpdateChain<AppTask> updateChain = mock(UpdateChain.class, RETURNS_SELF);

        AppTask startedTask = AppTask.builder()
                .id(task.getId())
                .appId(task.getAppId())
                .userId(task.getUserId())
                .taskType(task.getTaskType())
                .status(AppTaskStatus.RUNNING.name())
                .currentStep(startedStep.name())
                .build();

        when(appTaskService.getOne(any(QueryWrapper.class))).thenReturn(task);
        when(appTaskService.updateChain()).thenReturn(updateChain);
        when(updateChain.set(any(QueryColumn.class), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        when(appTaskService.getById(2L)).thenReturn(startedTask);

        AppTaskRuntimeServiceImpl service = new AppTaskRuntimeServiceImpl(
                appTaskService,
                appTaskLogPublisher,
                appTaskSseBroker,
                appCreateTaskRunner,
                appIterationTaskRunner,
                Runnable::run
        );
        return new Fixture(service, updateChain, appCreateTaskRunner, appIterationTaskRunner);
    }

    private AppTask task(AppTaskType taskType, AppTaskStatus status) {
        return AppTask.builder()
                .id(2L)
                .appId(1L)
                .userId(10L)
                .taskType(taskType.name())
                .status(status.name())
                .build();
    }

    private record Fixture(
            AppTaskRuntimeServiceImpl service,
            UpdateChain<AppTask> updateChain,
            AppCreateTaskRunner appCreateTaskRunner,
            AppIterationTaskRunner appIterationTaskRunner
    ) {
    }
}
