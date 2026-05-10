package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM 调用监控概览。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmMonitoringOverview {

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long callTotal;

    private Double successRate;

    private Long averageDurationMillis;

    private Long inputTokens;

    private Long outputTokens;

    private Long totalTokens;

    private Long errorCount;
}
