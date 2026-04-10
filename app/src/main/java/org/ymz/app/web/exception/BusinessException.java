package org.ymz.app.web.exception;

import lombok.Getter;
import org.ymz.app.web.response.ResultCode;

/**
 * 业务异常类
 * @author ymz
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    private BusinessException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.resultCode = resultCode;
    }

    /**
     * 使用默认提示信息创建业务异常
     */
    public static BusinessException of(ResultCode resultCode) {
        return new BusinessException(resultCode, resultCode.getMessage(), null);
    }

    /**
     * 使用自定义提示信息创建业务异常
     */
    public static BusinessException of(ResultCode resultCode, String message) {
        return new BusinessException(resultCode, message, null);
    }

    /**
     * 包装原始异常，使用默认提示信息创建业务异常
     */
    public static BusinessException of(ResultCode resultCode, Throwable cause) {
        return new BusinessException(resultCode, resultCode.getMessage(), cause);
    }

    /**
     * 包装原始异常，使用自定义提示信息创建业务异常
     */
    public static BusinessException of(ResultCode resultCode, String message, Throwable cause) {
        return new BusinessException(resultCode, message, cause);
    }
}