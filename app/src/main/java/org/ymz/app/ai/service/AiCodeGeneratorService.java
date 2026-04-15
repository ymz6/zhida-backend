package org.ymz.app.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import org.ymz.app.ai.dto.HtmlCodeResult;
import org.ymz.app.ai.dto.MultiFileCodeResult;

/**
 * AI 代码生成
 * @author ymz
 */
@AiService
public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.md")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件（index.html、style.css、script.js） 代码
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.md")
    MultiFileCodeResult generateMultiFileCode(String userMessage);
}
