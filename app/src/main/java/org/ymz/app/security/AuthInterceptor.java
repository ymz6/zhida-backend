package org.ymz.app.security;

import cn.hutool.core.util.StrUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.ymz.app.enums.UserRole;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.util.Date;

/**
 * 认证拦截器
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtHelper jwtHelper;
    private final AccessTokenBlacklistManager accessTokenBlacklistManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 仅拦截 Controller 方法：非 HandlerMethod，如静态资源，直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 检查目标请求处理方法有无 @LoginRequired注解，没有则直接放行
        LoginRequired methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), LoginRequired.class);
        LoginRequired classAnnotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), LoginRequired.class);
        boolean hasLoginRequired = methodAnnotation != null || classAnnotation != null;
        if (!hasLoginRequired) {
            return true;
        }

        // 解析 Access Token
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : null;
        if (StrUtil.isBlank(accessToken)) {
            throw BusinessException.of(ResultCode.NOT_LOGIN, "未登录");
        }

        Claims claims = null;
        try {
            claims = jwtHelper.parse(accessToken);
        } catch (ExpiredJwtException e) {
            throw BusinessException.of(ResultCode.NOT_LOGIN, "会话已过期，请重新登录");
        } catch (JwtException e) {
            throw BusinessException.of(ResultCode.NOT_LOGIN, "未登录");
        }
        // 提取 Claims
        Object userIdClaim = claims.get(JwtClaimName.USER_ID);
        Object roleClaim = claims.get(JwtClaimName.ROLE);
        String tokenId = claims.get(JwtClaimName.JTI, String.class);
        Date expiration = claims.getExpiration();

        if (!(userIdClaim instanceof Number userIdNumber) || !(roleClaim instanceof Number roleNumber)) {
            throw BusinessException.of(ResultCode.NOT_LOGIN, "未登录");
        }

        Long userId = userIdNumber.longValue();
        UserRole userRole = UserRole.fromCode(roleNumber.intValue());

        // 校验 tokenId 是否在黑名单中
        if (accessTokenBlacklistManager.isRevoked(tokenId)) {
            throw BusinessException.of(ResultCode.NOT_LOGIN, "未登录");
        }

        // 给当前请求处理线程设置用户认证上下文
        AuthContextHolder.set(
                AuthContext.builder()
                        .userId(userId)
                        .userRole(userRole)
                        .tokenId(tokenId)
                        .tokenExpiration(expiration)
                        .build()
        );
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 清除当前请求处理线程的用户认证上下文
        AuthContextHolder.clear();
    }
}
