package org.ymz.app.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统异常明细日志。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("system_exception_log")
public class SystemExceptionLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
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
