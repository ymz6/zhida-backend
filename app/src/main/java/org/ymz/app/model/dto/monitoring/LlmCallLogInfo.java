package org.ymz.app.model.dto.monitoring;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大语言模型调用明细。
 *
 * @author ymz
 */
@Data
public class LlmCallLogInfo {

    private Long id;

    private String scenario;

    private String modelName;

    private Long appId;

    private Long taskId;

    private String status;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

    private Long durationMillis;

    private String errorMessage;

    private LocalDateTime createdAt;
}
