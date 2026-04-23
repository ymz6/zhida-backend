package org.ymz.app.ai.service;

import dev.langchain4j.service.SystemMessage;
import org.ymz.app.ai.dto.TitleGenerateResult;

/**
 * 应用标题生成助手
 * @author ymz
 */
public interface TitleGenerateAssistant {
    @SystemMessage(fromResource = "prompt/application-title-generator.md")
    TitleGenerateResult chat(String userMessage);
}
