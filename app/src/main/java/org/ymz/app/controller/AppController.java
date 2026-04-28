package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.model.dto.app.AppChatMessageInfo;
import org.ymz.app.model.dto.app.AppDetail;
import org.ymz.app.model.dto.app.AppSummary;
import org.ymz.app.model.dto.app.AppTaskInfo;
import org.ymz.app.model.dto.app.CreateAppIterationRequest;
import org.ymz.app.model.dto.app.CreateAppRequest;
import org.ymz.app.model.dto.app.CreateAppTaskResponse;
import org.ymz.app.model.dto.app.DeployAppResponse;
import org.ymz.app.model.dto.app.ListAppMessagesRequest;
import org.ymz.app.model.dto.app.ListAppTasksRequest;
import org.ymz.app.model.dto.app.ListAppsRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.AppCreationService;
import org.ymz.app.service.AppDeploymentService;
import org.ymz.app.service.AppIterationService;
import org.ymz.app.service.AppQueryService;
import org.ymz.app.web.response.Response;

/**
 * 应用生成管理模块
 *
 * @author ymz
 */
@Tag(name = "app")
@LoginRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/apps")
public class AppController {

    private final AppCreationService appCreationService;
    private final AppIterationService appIterationService;
    private final AppDeploymentService appDeploymentService;
    private final AppQueryService appQueryService;

    @GetMapping
    @Operation(operationId = "listApps")
    public Response<PageResult<AppSummary>> listApps(@Validated ListAppsRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.listApps(authContext.getUserId(), request));
    }

    @GetMapping("/{appId}")
    @Operation(operationId = "getApp")
    public Response<AppDetail> getApp(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.getApp(authContext.getUserId(), appId));
    }

    @PostMapping
    @Operation(operationId = "createApp")
    public Response<CreateAppTaskResponse> createApp(@RequestBody @Valid CreateAppRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appCreationService.createApp(authContext.getUserId(), request));
    }

    @PostMapping("/{appId}/iterations")
    @Operation(operationId = "createAppIteration")
    public Response<CreateAppTaskResponse> createAppIteration(
            @PathVariable Long appId,
            @RequestBody @Valid CreateAppIterationRequest request
    ) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appIterationService.createAppIteration(authContext.getUserId(), appId, request));
    }

    @GetMapping("/{appId}/tasks")
    @Operation(operationId = "listAppTasks")
    public Response<PageResult<AppTaskInfo>> listAppTasks(
            @PathVariable Long appId,
            @Validated ListAppTasksRequest request
    ) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.listAppTasks(authContext.getUserId(), appId, request));
    }

    @GetMapping("/{appId}/messages")
    @Operation(operationId = "listAppMessages")
    public Response<PageResult<AppChatMessageInfo>> listAppMessages(
            @PathVariable Long appId,
            @Validated ListAppMessagesRequest request
    ) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.listAppMessages(authContext.getUserId(), appId, request));
    }

    @PostMapping("/{appId}/deploy")
    @Operation(operationId = "deployApp")
    public Response<DeployAppResponse> deployApp(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appDeploymentService.deployApp(authContext.getUserId(), appId));
    }
}
