package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.ymz.app.model.dto.app.*;
import org.ymz.app.model.dto.audit.AuditRecordVO;
import org.ymz.app.model.dto.audit.ListAuditRecordsRequest;
import org.ymz.app.model.dto.page.CursorResult;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.AppAuditService;
import org.ymz.app.service.AppOperationService;
import org.ymz.app.service.AppQueryService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.Response;
import org.ymz.app.web.response.ResultCode;
import reactor.core.publisher.Flux;

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
    private final AppAuditService appAuditService;

    // 分页查询应用列表
    @GetMapping
    @Operation(operationId = "listApps")
    public Response<PageResult<AppVO>> listApps(@Validated ListAppsRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.listApps(authContext, request));
    }

    // 查询应用详情
    @GetMapping("/{appId}")
    @Operation(operationId = "getApp")
    public Response<AppVO> getApp(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.getApp(authContext, appId));
    }

    // TODO 后续优化想法：创建应用是一个长耗时操作（因为初始化应用的逻辑在），我想可否通过 SSE 返回进度，提升用户体验？
    // 以后所有耗时操作都可以考虑这样优化？
    // 创建应用 已经稳定
    @PostMapping
    @Operation(operationId = "createApp")
    public Response<Long> createApp(@RequestBody @Valid CreateAppRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appOperationService.createApp(authContext.getUserId(), request));
    }

    // 编辑应用信息
    @PutMapping("/{appId}")
    @Operation(operationId = "editApp")
    public Response<AppVO> editApp(@PathVariable Long appId, @RequestBody @Valid EditAppRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appOperationService.editApp(authContext, appId, request));
    }

    // 打开应用工作区文件或目录
    @GetMapping("/{appId}/files/open")
    @Operation(operationId = "openAppFile")
    public Response<FileNode> openAppFile(@PathVariable Long appId, @RequestParam(required = false) String path) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appOperationService.openAppFile(authContext, appId, path));
    }

    // 删除应用
    @DeleteMapping("/{appId}")
    @Operation(operationId = "deleteApp")
    public Response<Void> deleteApp(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        appOperationService.deleteApp(authContext, appId);
        return Response.ok();
    }

    // 下载应用源码压缩包
    @GetMapping("/{appId}/download")
    @Operation(operationId = "downloadAppSourceCode")
    public ResponseEntity<StreamingResponseBody> downloadAppSourceCode(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        String filename = "zhida-app-" + appId + ".zip";
        StreamingResponseBody responseBody = outputStream -> appOperationService.downloadAppSourceCode(authContext,
                appId, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename)
                        .build()
                        .toString())
                .body(responseBody);
    }

    // 预览应用 已经稳定
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

    // 应用部署 已经稳定
    @PostMapping("/{appId}/deploy")
    @Operation(operationId = "deployApp")
    public Response<String> deployApp(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appOperationService.deployApp(authContext.getUserId(), appId));
    }

    // 通过聊天生成应用
    @PostMapping(value = "/{appId}/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(operationId = "chatWithApp")
    public Flux<ChatStreamMessage> chatWithApp(@PathVariable Long appId, @RequestBody @Valid ChatRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return appOperationService.chat(authContext.getUserId(), appId, request);
    }

    // 查询应用聊天消息
    @GetMapping("/{appId}/messages")
    @Operation(operationId = "listAppMessages")
    public Response<CursorResult<AppChatMessageVO>> listAppMessages(
            @PathVariable Long appId,
            @Validated ListAppMessagesRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appQueryService.listAppMessages(authContext, appId, request));
    }

    // 提交应用案例审核
    @PostMapping("/{appId}/audit/submit")
    @Operation(operationId = "submitAppAudit")
    public Response<Void> submitAppAudit(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        appAuditService.submitAudit(authContext, appId);
        return Response.ok();
    }

    // 撤回应用案例审核
    @PostMapping("/{appId}/audit/withdraw")
    @Operation(operationId = "withdrawAppAudit")
    public Response<Void> withdrawAppAudit(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        appAuditService.withdrawAudit(authContext, appId);
        return Response.ok();
    }

    // 查询应用审核记录
    @GetMapping("/{appId}/audit-records")
    @Operation(operationId = "listAppAuditRecords")
    public Response<PageResult<AuditRecordVO>> listAppAuditRecords(
            @PathVariable Long appId,
            @Validated ListAuditRecordsRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appAuditService.listAppAuditRecords(authContext, appId, request));
    }

}
