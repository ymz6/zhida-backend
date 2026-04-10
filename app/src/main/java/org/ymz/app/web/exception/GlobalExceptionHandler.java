package org.ymz.app.web.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
     * 处理未捕获的运行时异常（非预期失败）
     */
    @ExceptionHandler(RuntimeException.class)
    public Response<Void> handleRuntimeException(RuntimeException e) {
        log.error("Unhandled runtime exception", e);
        return Response.fail(ResultCode.SYSTEM_ERROR);
    }
}
