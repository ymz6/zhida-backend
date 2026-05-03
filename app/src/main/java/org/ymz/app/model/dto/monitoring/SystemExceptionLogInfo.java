package org.ymz.app.model.dto.monitoring;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统异常明细。
 *
 * @author ymz
 */
@Data
public class SystemExceptionLogInfo {

    private Long id;

    private String exceptionType;

    private Integer resultCode;

    private String requestMethod;

    private String requestPath;

    private String errorMessage;

    private String stackTrace;

    private Long userId;

    private LocalDateTime createdAt;
}
