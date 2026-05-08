package org.ymz.app.ai.codegen.agent;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 对话答疑场景 AI Service：仅可读取代码，不可修改。
 *
 * @author ymz
 */
public interface ChatCodeGenerationAiService {

    @SystemMessage(fromResource = "prompt/code-gen-chat-agent.md")
    TokenStream chat(
            @MemoryId String memoryId,
            @UserMessage String userMessage,
            InvocationParameters invocationParameters
    );
}
