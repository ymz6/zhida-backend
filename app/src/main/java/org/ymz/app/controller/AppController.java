package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ymz.app.model.dto.app.AppChatMessageInfo;
import org.ymz.app.model.dto.app.AppDetail;
import org.ymz.app.model.dto.app.AppSummary;
import org.ymz.app.model.dto.app.ChatRequest;
import org.ymz.app.model.dto.app.CreateAppRequest;
import org.ymz.app.model.dto.app.CreateAppResponse;
import org.ymz.app.model.dto.app.DeployAppResponse;
import org.ymz.app.model.dto.app.ListAppMessagesRequest;
import org.ymz.app.model.dto.app.ListMyAppsRequest;
import org.ymz.app.model.dto.page.CursorResult;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.AppChatService;
import org.ymz.app.service.AppOperationService;
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

    private final AppOperationService appOperationService;
    private final AppQueryService appQueryService;
    private final AppChatService appChatService;

    @GetMapping("/mine")
    @Operation(operationId = "listMyApps")
    public Response<PageResult<AppSummary>> listMyApps(@Validated ListMyAppsRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.listMyApps(authContext.getUserId(), request));
    }

    @GetMapping("/{appId}")
    @Operation(operationId = "getApp")
    public Response<AppDetail> getApp(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.getApp(authContext.getUserId(), appId));
    }

    @PostMapping
    @Operation(operationId = "createApp")
    public Response<CreateAppResponse> createApp(@RequestBody @Valid CreateAppRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appOperationService.createApp(authContext.getUserId(), request));
    }

    @PostMapping(value = "/{appId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(operationId = "chatWithApp")
    public SseEmitter chatWithApp(@PathVariable Long appId, @RequestBody @Valid ChatRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return appChatService.chat(authContext.getUserId(), appId, request);
    }

    @GetMapping("/{appId}/messages")
    @Operation(operationId = "listAppMessages")
    public Response<CursorResult<AppChatMessageInfo>> listAppMessages(
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
        return Response.ok(appOperationService.deployApp(authContext.getUserId(), appId));
    }
}
