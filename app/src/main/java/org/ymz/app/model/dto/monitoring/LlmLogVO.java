package org.ymz.app.model.dto.monitoring;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * LLM 调用日志明细。
 *
 * @author ymz
 */
@Data
public class LlmLogVO {

    private Long id;

    private String modelName;

    private Long userId;

    private String status;

    private Long inputTokens;

    private Long outputTokens;

    private Long totalTokens;

    private String usageJson;

    private Long durationMillis;

    private String errorMessage;

    private LocalDateTime createdAt;
}
