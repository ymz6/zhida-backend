package org.ymz.app.model.dto.app.content;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Assistant 回复的结构化内容块。
 *
 * @author ymz
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextBlock.class, name = "text"),
        @JsonSubTypes.Type(value = ToolUseBlock.class, name = "tool_use")
})
public sealed interface ContentBlock permits TextBlock, ToolUseBlock {
}
