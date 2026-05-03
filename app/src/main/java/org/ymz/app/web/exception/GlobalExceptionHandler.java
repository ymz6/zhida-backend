package org.ymz.app.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.ymz.app.monitoring.SystemExceptionRecorder;
import org.ymz.app.web.response.Response;
import org.ymz.app.web.response.ResultCode;

import java.util.stream.Collectors;


/**
 * 全局异常处理器
 *
 * @author ymz
 */
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SystemExceptionRecorder systemExceptionRecorder;

    /**
     * 处理业务异常（预期内失败）
     */
    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        systemExceptionRecorder.record(e, e.getResultCode(), e.getMessage(), request);
        return Response.fail(e.getResultCode(), e.getMessage());
    }

    /**
     * 处理 Spring 参数校验异常：
     * BindException 处理对象参数校验
     * HandlerMethodValidationException 处理方法参数校验
     */
    @ExceptionHandler(BindException.class)
    public Response<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        systemExceptionRecorder.record(e, ResultCode.INVALID_PARAM, message, request);
        return Response.fail(ResultCode.INVALID_PARAM, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public Response<Void> handleValidationException(
            HandlerMethodValidationException e,
            HttpServletRequest request
    ) {
        systemExceptionRecorder.record(e, ResultCode.INVALID_PARAM, e.getMessage(), request);
        return Response.fail(ResultCode.INVALID_PARAM, e.getMessage());
    }

    /**
     * 处理未捕获的运行时异常（非预期失败）
     */
    @ExceptionHandler(RuntimeException.class)
    public Response<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("Unhandled runtime exception", e);
        systemExceptionRecorder.record(e, ResultCode.SYSTEM_ERROR, e.getMessage(), request);
        return Response.fail(ResultCode.SYSTEM_ERROR, e.getMessage());
    }
}
