package org.ymz.app.ai.codegen.agent;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 创建应用场景代码生成 AI Service。
 *
 * @author ymz
 */
public interface CreateCodeGenerationAiService {

    @SystemMessage(fromResource = "prompt/code-gen-create-agent.md")
    TokenStream generate(
            @MemoryId String memoryId,
            @UserMessage String userMessage,
            InvocationParameters invocationParameters
    );
}
