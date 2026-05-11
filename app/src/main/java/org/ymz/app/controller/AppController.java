package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ymz.app.model.dto.app.*;
import org.ymz.app.model.dto.page.CursorResult;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.AppChatService;
import org.ymz.app.service.AppOperationService;
import org.ymz.app.service.AppQueryService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.Response;
import org.ymz.app.web.response.ResultCode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    // 意义不明
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

    // 预览应用
    @GetMapping("/preview/{appId}/**")
    @Operation(operationId = "previewApp")
    public ResponseEntity<Resource> previewApp(@PathVariable Long appId, HttpServletRequest request)
            throws IOException {
        // 从 request 解析 resourcePath
        String requestPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefix = "/apps/preview/" + appId;
        if (!requestPath.startsWith(prefix)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM);
        }
        String resourcePath = requestPath.substring(prefix.length());

        // 访问 /preview/{appId} 时，重定向到 /preview/{appId}/，保证相对路径资源能带上 appId
        if (resourcePath.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, request.getRequestURI() + "/")
                    .build();
        }
        if (resourcePath.isEmpty()) {
            resourcePath = "/";
        }
        AuthContext authContext = AuthContextHolder.get();
        Path filePath = appOperationService.getPreviewFilePath(appId, resourcePath, authContext);
        File file = filePath.toFile();
        // 判断文件是否存在
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        String contentType = Files.probeContentType(file.toPath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(resource);
    }

    // 要重新设计
    @PostMapping
    @Operation(operationId = "createApp")
    public Response<CreateAppResponse> createApp(@RequestBody @Valid CreateAppRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appOperationService.createApp(authContext.getUserId(), request));
    }

    // 要重新设计
    @PostMapping(value = "/{appId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(operationId = "chatWithApp")
    public SseEmitter chatWithApp(@PathVariable Long appId, @RequestBody @Valid ChatRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return appChatService.chat(authContext.getUserId(), appId, request);
    }

    // 要重新设计
    @GetMapping("/{appId}/messages")
    @Operation(operationId = "listAppMessages")
    public Response<CursorResult<AppChatMessageInfo>> listAppMessages(
            @PathVariable Long appId,
            @Validated ListAppMessagesRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.listAppMessages(authContext.getUserId(), appId, request));
    }

    // 复习一下，然后重新设计
    @PostMapping("/{appId}/deploy")
    @Operation(operationId = "deployApp")
    public Response<String> deployApp(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appOperationService.deployApp(authContext.getUserId(), appId));
    }
}
