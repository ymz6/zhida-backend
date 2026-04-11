package org.ymz.app.web.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.ymz.app.web.response.Response;
import org.ymz.app.web.response.ResultCode;


/**
 * 全局异常处理器
 *
 * @author ymz
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 处理业务异常（预期内失败）
     */
    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusinessException(BusinessException e) {
        return Response.fail(e.getResultCode(), e.getMessage());
    }

    /**
     * 处理 Spring 参数校验异常：
     * BindException 处理对象参数校验
     * HandlerMethodValidationException 处理方法参数校验
     */
    @ExceptionHandler({BindException.class, HandlerMethodValidationException.class})
    public Response<Void> handleValidationException(Exception e) {
        return Response.fail(ResultCode.INVALID_PARAM);
    }

    /**
     * 处理未捕获的运行时异常（非预期失败）
     */
    @ExceptionHandler(RuntimeException.class)
    public Response<Void> handleRuntimeException(RuntimeException e) {
        log.error("Unhandled runtime exception", e);
        return Response.fail(ResultCode.SYSTEM_ERROR);
    }
}
