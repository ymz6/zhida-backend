package org.ymz.app.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.ymz.app.config.AppPathProperties;
import org.ymz.app.model.dto.app.PreviewSessionVO;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.enums.UserRole;
import org.ymz.app.security.AuthContext;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * 工作台预览会话与预览资源路径服务。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class PreviewSessionService {

    public static final String COOKIE_NAME = "preview_session";
    public static final long EXPIRES_SECONDS = 300L;

    private static final String REDIS_KEY_PREFIX = "preview-session:";

    private final AppService appService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AppPathProperties appPathProperties;

    public PreviewSessionVO createPreviewSession(AuthContext authContext, Long appId) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!authContext.getUserId().equals(app.getUserId()) && !UserRole.ADMIN.equals(authContext.getUserRole())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }

        Path indexPath = getPreviewFilePath(appId, "/index.html");
        if (!Files.isRegularFile(indexPath)) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "预览资源不存在");
        }

        String token = IdUtil.fastSimpleUUID();
        long expiresAt = System.currentTimeMillis() + Duration.ofSeconds(EXPIRES_SECONDS).toMillis();
        stringRedisTemplate.opsForValue().set(REDIS_KEY_PREFIX + token, JSONUtil.toJsonStr(Map.of(
                "userId", authContext.getUserId(),
                "appId", appId,
                "expiresAt", expiresAt
        )), Duration.ofSeconds(EXPIRES_SECONDS));

        return PreviewSessionVO.builder()
                .previewUrl("/apps/preview/" + appId + "/index.html")
                .expiresIn(EXPIRES_SECONDS)
                .token(token)
                .build();
    }

    public void validatePreviewSession(Long appId, String token) {
        if (StrUtil.isBlank(token)) {
            throw BusinessException.of(ResultCode.NOT_LOGIN, "预览会话已失效");
        }

        String sessionJson = stringRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + token);
        if (StrUtil.isBlank(sessionJson)) {
            throw BusinessException.of(ResultCode.NOT_LOGIN, "预览会话已失效");
        }

        var session = JSONUtil.parseObj(sessionJson);
        Long sessionAppId = session.getLong("appId");
        if (!appId.equals(sessionAppId)) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }

        Long expiresAt = session.getLong("expiresAt");
        if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
            stringRedisTemplate.delete(REDIS_KEY_PREFIX + token);
            throw BusinessException.of(ResultCode.NOT_LOGIN, "预览会话已失效");
        }
    }

    public Path resolvePreviewResourcePath(Long appId, String resourcePath) {
        String normalizedResourcePath = normalizeResourcePath(resourcePath);
        Path targetPath = getPreviewFilePath(appId, normalizedResourcePath);
        if (Files.isRegularFile(targetPath)) {
            return targetPath;
        }

        if (isSpaRoute(normalizedResourcePath)) {
            Path indexPath = getPreviewFilePath(appId, "/index.html");
            if (Files.isRegularFile(indexPath)) {
                return indexPath;
            }
        }

        throw BusinessException.of(ResultCode.NOT_FOUND);
    }

    private Path getPreviewFilePath(Long appId, String resourcePath) {
        Path previewRootPath = appPathProperties.getTmpDir()
                .resolve("app-previews")
                .resolve(String.valueOf(appId))
                .normalize();
        Path targetPath = previewRootPath
                // 去掉开头的 /，确保资源路径只能在当前应用预览目录内解析。
                .resolve(resourcePath.substring(1))
                .normalize();
        if (!targetPath.startsWith(previewRootPath)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM);
        }
        return targetPath;
    }

    private String normalizeResourcePath(String resourcePath) {
        if (StrUtil.isBlank(resourcePath) || "/".equals(resourcePath)) {
            return "/index.html";
        }
        return resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    }

    private boolean isSpaRoute(String resourcePath) {
        String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        return !lastSegment.contains(".");
    }
}
