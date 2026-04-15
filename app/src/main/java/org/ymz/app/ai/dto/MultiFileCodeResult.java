package org.ymz.app.ai.dto;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 *
 * @author ymz
 */
@Data
@Description("多文件代码结果")
public class MultiFileCodeResult {
    @Description("HTML代码内容")
    private String htmlContent;
    @Description("CSS代码内容")
    private String cssContent;
    @Description("JavaScript代码内容")
    private String jsContent;
}
