package org.ymz.app.model.dto.app.content;

/**
 * 工具调用内容块。
 *
 * @author ymz
 */
public record ToolUseBlock(
        String name,
        Object input,
        String result
) implements ContentBlock {
}
