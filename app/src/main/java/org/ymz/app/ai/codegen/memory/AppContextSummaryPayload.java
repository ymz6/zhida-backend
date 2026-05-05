package org.ymz.app.ai.codegen.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用长期上下文摘要。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppContextSummaryPayload {

    private String appName;

    @Builder.Default
    private List<String> pages = new ArrayList<>();

    @Builder.Default
    private List<String> routes = new ArrayList<>();

    @Builder.Default
    private List<String> coreFeatures = new ArrayList<>();

    @Builder.Default
    private List<String> stateModels = new ArrayList<>();

    @Builder.Default
    private List<String> visualStyles = new ArrayList<>();

    @Builder.Default
    private List<String> constraints = new ArrayList<>();

    @Builder.Default
    private List<String> knownIssues = new ArrayList<>();
}
