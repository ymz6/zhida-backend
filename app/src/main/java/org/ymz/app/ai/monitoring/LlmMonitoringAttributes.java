package org.ymz.app.ai.monitoring;

import org.ymz.app.model.enums.codegen.CodeGenerationScenario;

/**
 * LLM 调用监听器上下文属性名。
 *
 * @author ymz
 */
public final class LlmMonitoringAttributes {

    public static final String SCENARIO = "zhida.llm.scenario";
    public static final String APP_ID = "zhida.llm.appId";
    public static final String TASK_ID = "zhida.llm.taskId";
    public static final String CONTEXT = "llmMonitoringContext";
    public static final String START_NANOS = "zhida.llm.startNanos";

    public static final String SCENARIO_TITLE_GENERATION = "TITLE_GENERATION";
    public static final String SCENARIO_CONTEXT_SUMMARY = "CONTEXT_SUMMARY";
    public static final String SCENARIO_CODE_GENERATION_CREATE = CodeGenerationScenario.CREATE.getMonitoringScenario();
    public static final String SCENARIO_CODE_GENERATION_ITERATE = CodeGenerationScenario.ITERATE.getMonitoringScenario();
    public static final String SCENARIO_CODE_GENERATION_REPAIR = CodeGenerationScenario.REPAIR.getMonitoringScenario();

    private LlmMonitoringAttributes() {
    }
}
