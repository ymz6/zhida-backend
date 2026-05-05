package org.ymz.app.ai.codegen.runtime;

import lombok.Builder;
import lombok.Data;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.model.enums.codegen.CodeGenerationScenario;

import java.nio.file.Path;

/**
 * 单次代码生成调用上下文。
 *
 * @author ymz
 */
@Data
@Builder
public class CodeGenerationContext {

    private Long appId;

    private Long taskId;

    private String appName;

    private Path workspacePath;

    private CodeGenerationScenario scenario;

    private String taskPrompt;

    private CodeGenerationCommandResult failedCommand;

    private Integer repairAttempt;

    public String memoryId() {
        if (appId == null) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        return "app:" + appId;
    }

    public boolean isRepair() {
        return scenario == CodeGenerationScenario.REPAIR;
    }
}
