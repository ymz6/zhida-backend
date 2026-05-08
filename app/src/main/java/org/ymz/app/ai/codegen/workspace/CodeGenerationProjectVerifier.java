package org.ymz.app.ai.codegen.workspace;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 统一执行代码生成项目校验。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class CodeGenerationProjectVerifier {

    private final CodeGenerationCommandRunner commandRunner;

    public CodeGenerationCommandResult verify(Long appId, Long taskId, Path workspacePath) {
        CodeGenerationCommandResult lintResult = commandRunner.runPnpmCommandResult(
                appId,
                taskId,
                workspacePath,
                CodeGenerationCommandRunner.LogMode.SUMMARY,
                "lint"
        );
        if (!lintResult.isSuccess()) {
            return lintResult;
        }

        // 生成预览需要保留 JSX 插桩元数据，正式部署会单独执行 pnpm build。
        CodeGenerationCommandResult buildResult = commandRunner.runPnpmCommandResult(
                appId,
                taskId,
                workspacePath,
                CodeGenerationCommandRunner.LogMode.SUMMARY,
                "build:preview"
        );
        if (!buildResult.isSuccess()) {
            return buildResult;
        }
        return null;
    }
}
