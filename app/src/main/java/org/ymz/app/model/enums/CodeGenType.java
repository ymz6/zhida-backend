package org.ymz.app.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

/**
 * 代码生成类型
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum CodeGenType {
    HTML(0),
    MULTI_FILE(1);

    private final int code;

    public static CodeGenType fromCode(int code) {
        for (CodeGenType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown CodeGenType code: " + code);
    }
    public String getText() {
        return name().toLowerCase(Locale.ROOT);
    }
}
