package org.ymz.app.model.enums.app;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;

/**
 * 文件节点类型
 *
 * @author ymz
 */
@AllArgsConstructor
public enum FileNodeType {

    FILE("file"),
    DIRECTORY("directory");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }
}
