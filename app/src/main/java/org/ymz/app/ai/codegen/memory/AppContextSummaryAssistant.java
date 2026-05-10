package org.ymz.app.ai.codegen.memory;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 生成应用长期上下文摘要。
 *
 * @author ymz
 */
public interface AppContextSummaryAssistant {

    @SystemMessage(fromResource = "prompt/app-context-summary.md")
    AppContextSummaryPayload summarize(@UserMessage String userMessage, InvocationParameters invocationParameters);
}
