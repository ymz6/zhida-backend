package org.ymz.app.monitoring;

/**
 * LLM 调用监听器上下文属性名。
 *
 * @author ymz
 */
public final class LlmMonitoringAttributes {

    public static final String SCENARIO = "zhida.llm.scenario";
    public static final String APP_ID = "zhida.llm.appId";
    public static final String TASK_ID = "zhida.llm.taskId";
    public static final String START_NANOS = "zhida.llm.startNanos";

    public static final String SCENARIO_TITLE_GENERATION = "TITLE_GENERATION";
    public static final String SCENARIO_CODE_GENERATION_CREATE = "CODE_GENERATION_CREATE";
    public static final String SCENARIO_CODE_GENERATION_ITERATE = "CODE_GENERATION_ITERATE";
    public static final String SCENARIO_CODE_GENERATION_REPAIR = "CODE_GENERATION_REPAIR";

    private LlmMonitoringAttributes() {
    }
}
