package org.ymz.app.model.enums.codegen;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 代码生成场景。
 *
 * @author ymz
 */
@Getter
@RequiredArgsConstructor
public enum CodeGenerationScenario {

    CREATE("CODE_GENERATION_CREATE"),
    ITERATE("CODE_GENERATION_ITERATE"),
    REPAIR("CODE_GENERATION_REPAIR");

    private final String monitoringScenario;
}
