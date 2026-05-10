package org.ymz.app.ai.monitoring;

/**
 * LLM 调用监控上下文，随 LangChain4j InvocationParameters 传入。
 *
 * @author ymz
 */
public record LlmMonitoringContext(
        String scenario,
        Long appId,
        Long taskId
) {
}
