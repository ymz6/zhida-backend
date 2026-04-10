package org.ymz.app.web.response;

import lombok.AllArgsConstructor;

/**
 * 统一响应类
 *
 * @author ymz
 */
public record Response<T>(int code, String message, T data) {
    /**
     * 构建携带数据的成功响应
     */
    public static <T> Response<T> ok(T data) {
        ResultCode success = ResultCode.SUCCESS;
        return new Response<>(success.getCode(), success.getMessage(), data);
    }

    /**
     * 构建无需数据的成功响应
     */
    public static Response<Void> ok() {
        ResultCode success = ResultCode.SUCCESS;
        return new Response<>(success.getCode(), success.getMessage(), null);
    }

    /**
     * 用默认提示信息构建失败响应
     */
    public static Response<Void> fail(ResultCode businessStatus) {
        return new Response<>(businessStatus.getCode(), businessStatus.getMessage(), null);
    }

    /**
     * 用自定义提示信息构建失败响应
     */
    public static Response<Void> fail(ResultCode businessStatus, String message) {
        return new Response<>(businessStatus.getCode(), message, null);
    }
}