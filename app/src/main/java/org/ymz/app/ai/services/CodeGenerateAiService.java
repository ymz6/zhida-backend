package org.ymz.app.ai.services;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 代码生成助手
 * @author ymz
 */
public interface CodeGenerateAiService {
    @SystemMessage(fromResource = "prompt/code-gen-agent.md")
    TokenStream chat(@MemoryId Long appId, @UserMessage String userMessage, InvocationParameters parameters);
}
