package org.ymz.app.model.dto.monitoring;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 调用概览数据。
 *
 * @author ymz
 */
@Data
public class LlmLogOverviewVO {

    private Long totalTokens = 0L;

    private Long inputTokens = 0L;

    private Long outputTokens = 0L;

    private Double inputTokenRatio = 0.0;

    private Double outputTokenRatio = 0.0;

    private Long totalCalls = 0L;

    private Long successCalls = 0L;

    private Long failedCalls = 0L;

    private Double successRate = 0.0;

    private Double averageDurationMillis;

    private List<LlmModelCallCountVO> modelCallCounts = new ArrayList<>();
}
