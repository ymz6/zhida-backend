package org.ymz.app.web.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 状态码枚举类
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(20000, "请求成功"),
    INVALID_PARAM(40000, "请求参数错误"),
    NOT_LOGIN(40100, "未登录"),
    NO_PERMISSION(40300, "无权限"),
    NOT_FOUND(40400, "请求数据不存在"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),
    SYSTEM_ERROR(50000, "系统内部异常");

    private final int code;
    private final String message;

}
