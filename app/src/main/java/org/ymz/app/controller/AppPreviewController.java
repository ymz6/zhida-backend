package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.HtmlUtils;
import org.ymz.app.model.dto.app.PreviewSessionVO;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.PreviewSessionService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.Response;
import org.ymz.app.web.response.ResultCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * 工作台应用预览资源服务。
 *
 * @author ymz
 */
@Tag(name = "app-preview")
@RestController
@RequiredArgsConstructor
@RequestMapping("/apps")
public class AppPreviewController {

    private final PreviewSessionService previewSessionService;

    @LoginRequired
    @PostMapping("/{appId}/preview-session")
    @Operation(operationId = "createPreviewSession")
    public Response<PreviewSessionVO> createPreviewSession(
            @PathVariable Long appId,
            HttpServletResponse servletResponse) {
        AuthContext authContext = AuthContextHolder.get();
        PreviewSessionVO session = previewSessionService.createPreviewSession(authContext, appId);
        ResponseCookie cookie = ResponseCookie.from(PreviewSessionService.COOKIE_NAME, session.getToken())
                .httpOnly(true)
                .path("/api/apps/preview/" + appId)
                .maxAge(Duration.ofSeconds(session.getExpiresIn()))
                .sameSite("Lax")
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return Response.ok(session);
    }

    @GetMapping("/preview/{appId}/**")
    @Operation(operationId = "previewApp")
    public ResponseEntity<?> previewApp(
            @PathVariable Long appId,
            @CookieValue(value = PreviewSessionService.COOKIE_NAME, required = false) String previewSession,
            HttpServletRequest request) {
        try {
            previewSessionService.validatePreviewSession(appId, previewSession);

            String requestPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            String prefix = "/apps/preview/" + appId;
            if (requestPath == null || !requestPath.startsWith(prefix)) {
                throw BusinessException.of(ResultCode.INVALID_PARAM);
            }

            String resourcePath = requestPath.substring(prefix.length());
            if (resourcePath.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.FOUND)
                        .cacheControl(CacheControl.noStore())
                        .header(HttpHeaders.LOCATION, request.getRequestURI() + "/index.html")
                        .build();
            }

            Path filePath = previewSessionService.resolvePreviewResourcePath(appId, resourcePath);
            String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
            int dotIndex = fileName.lastIndexOf('.');
            String extension = dotIndex < 0 ? "" : fileName.substring(dotIndex);
            String contentType = switch (extension) {
                case ".html" -> "text/html";
                case ".js" -> "application/javascript";
                case ".css" -> "text/css";
                case ".json" -> "application/json";
                case ".svg" -> "image/svg+xml";
                case ".png" -> "image/png";
                case ".jpg", ".jpeg" -> "image/jpeg";
                case ".webp" -> "image/webp";
                case ".ico" -> "image/x-icon";
                case ".woff" -> "font/woff";
                case ".woff2" -> "font/woff2";
                default -> Files.probeContentType(filePath);
            };
            Resource resource = new FileSystemResource(filePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType))
                    .cacheControl(CacheControl.noStore())
                    .body(resource);
        } catch (BusinessException e) {
            ResultCode resultCode = e.getResultCode();
            HttpStatus status = switch (resultCode) {
                case NOT_LOGIN -> HttpStatus.UNAUTHORIZED;
                case NO_PERMISSION -> HttpStatus.FORBIDDEN;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case INVALID_PARAM -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            String safeMessage = HtmlUtils.htmlEscape(e.getMessage() == null ? resultCode.getMessage() : e.getMessage());
            String html = """
                    <!doctype html>
                    <html lang="zh-CN">
                    <head>
                      <meta charset="UTF-8">
                      <title>预览不可用</title>
                    </head>
                    <body>
                      <h1>预览不可用</h1>
                      <p>业务码：%d</p>
                      <p>%s</p>
                    </body>
                    </html>
                    """.formatted(resultCode.getCode(), safeMessage);
            return ResponseEntity.status(status)
                    .contentType(MediaType.TEXT_HTML)
                    .cacheControl(CacheControl.noStore())
                    .body(html);
        } catch (Exception e) {
            String safeMessage = HtmlUtils.htmlEscape("预览资源读取失败");
            String html = """
                    <!doctype html>
                    <html lang="zh-CN">
                    <head>
                      <meta charset="UTF-8">
                      <title>预览不可用</title>
                    </head>
                    <body>
                      <h1>预览不可用</h1>
                      <p>业务码：%d</p>
                      <p>%s</p>
                    </body>
                    </html>
                    """.formatted(ResultCode.SYSTEM_ERROR.getCode(), safeMessage);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .cacheControl(CacheControl.noStore())
                    .body(html);
        }
    }
}
