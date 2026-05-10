package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM Token 用量趋势数据点。
 *
 * @author ymz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmTokenUsageTrendPoint {

    private LocalDateTime time;

    private Double value;
}
