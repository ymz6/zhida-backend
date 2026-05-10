package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM Token 用量趋势。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmTokenUsageTrend {

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long stepSeconds;

    @Builder.Default
    private List<LlmTokenUsageTrendSeries> series = new ArrayList<>();
}
