package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ymz.app.model.dto.app.AppTaskInfo;
import org.ymz.app.model.dto.task.TaskStatusResponse;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.AppQueryService;
import org.ymz.app.service.AppTaskRuntimeService;
import org.ymz.app.web.response.Response;

/**
 * 应用生成任务运行时接口
 *
 * @author ymz
 */
@Tag(name = "app-task")
@LoginRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/tasks")
public class AppTaskController {

    private final AppTaskRuntimeService appTaskRuntimeService;
    private final AppQueryService appQueryService;

    @GetMapping("/{taskId}")
    @Operation(operationId = "getTask")
    public Response<AppTaskInfo> getTask(@PathVariable Long taskId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.getTask(authContext.getUserId(), taskId));
    }

    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(operationId = "streamTask")
    public SseEmitter streamTask(@PathVariable Long taskId) {
        AuthContext authContext = AuthContextHolder.get();
        return appTaskRuntimeService.streamTask(authContext.getUserId(), taskId);
    }

    @PostMapping("/{taskId}/start")
    @Operation(operationId = "startTask")
    public Response<TaskStatusResponse> startTask(@PathVariable Long taskId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appTaskRuntimeService.startTask(authContext.getUserId(), taskId));
    }
}
