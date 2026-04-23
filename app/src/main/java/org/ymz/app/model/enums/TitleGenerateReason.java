package org.ymz.app.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标题生成原因
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum TitleGenerateReason {

    OK(""),
    TOO_SHORT("输入内容过短"),
    TOO_VAGUE("输入内容过于模糊"),
    MEANINGLESS_INPUT("输入内容无实际意义");

    private final String description;
}
