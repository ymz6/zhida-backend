package org.ymz.app.model.dto;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import org.ymz.app.model.enums.TitleGenerateReason;

/**
 *
 * @author ymz
 */
@Data
@Description("应用标题生成结果")
public class TitleGenerateResult {
    @Description("用户输入是否有效")
    private boolean accepted;
    @Description("生成的应用标题")
    private String title;
    @Description({"原因代称","可选值为 OK、TOO_SHORT、TOO_VAGUE、MEANINGLESS_INPUT"})
    private TitleGenerateReason reason;
}
