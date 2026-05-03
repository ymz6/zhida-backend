package org.ymz.app.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.ymz.app.model.entity.SystemExceptionLog;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.service.SystemExceptionLogService;
import org.ymz.app.web.response.ResultCode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * 记录异常明细并同步写入异常指标。
 *
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemExceptionRecorder {

    private static final int TEXT_LIMIT = 4000;

    private final SystemExceptionLogService systemExceptionLogService;
    private final SystemExceptionMetrics systemExceptionMetrics;

    public void record(Throwable throwable, ResultCode resultCode, String message, HttpServletRequest request) {
        String requestPath = resolveRequestPath(request);
        systemExceptionMetrics.record(throwable.getClass().getSimpleName(), resultCode.getCode(), requestPath);

        try {
            AuthContext authContext = AuthContextHolder.get();
            SystemExceptionLog log = SystemExceptionLog.builder()
                    .exceptionType(throwable.getClass().getName())
                    .resultCode(resultCode.getCode())
                    .requestMethod(request == null ? null : request.getMethod())
                    .requestPath(requestPath)
                    .errorMessage(limit(message))
                    .stackTrace(limit(stackTrace(throwable)))
                    .userId(authContext == null ? null : authContext.getUserId())
                    .createdAt(LocalDateTime.now())
                    .build();
            systemExceptionLogService.save(log);
        } catch (Exception e) {
            log.warn("记录系统异常明细失败", e);
        }
    }

    private String resolveRequestPath(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return String.valueOf(pattern);
        }
        return request.getRequestURI();
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String limit(String value) {
        if (value == null || value.length() <= TEXT_LIMIT) {
            return value;
        }
        return value.substring(0, TEXT_LIMIT);
    }
}
