package org.ymz.app.ai.services;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.ymz.app.model.dto.app.TitleGenerateResult;

/**
 * 应用标题生成助手
 * @author ymz
 */
public interface TitleGenerateAiService {
    @SystemMessage(fromResource = "prompt/application-title-generator.md")
    TitleGenerateResult chat(@UserMessage String userMessage);
}
