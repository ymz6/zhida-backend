package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM Token 用量趋势序列。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmTokenUsageTrendSeries {

    private String name;

    @Builder.Default
    private List<LlmTokenUsageTrendPoint> points = new ArrayList<>();
}
