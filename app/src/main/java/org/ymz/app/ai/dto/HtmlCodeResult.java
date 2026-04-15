package org.ymz.app.ai.dto;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 *
 * @author ymz
 */
@Data
@Description("单HTML文件结果")
public class HtmlCodeResult {
    @Description("HTML代码内容")
    private String htmlContent;
}
