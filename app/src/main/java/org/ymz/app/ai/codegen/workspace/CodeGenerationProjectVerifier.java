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
        CodeGenerationCommandResult lintResult = runLint(appId, taskId, workspacePath);
        if (!lintResult.isSuccess()) {
            return lintResult;
        }

        CodeGenerationCommandResult buildResult = runBuild(appId, taskId, workspacePath);
        if (!buildResult.isSuccess()) {
            return buildResult;
        }
        return null;
    }

    public CodeGenerationCommandResult runLint(Long appId, Long taskId, Path workspacePath) {
        return commandRunner.runPnpmCommandResult(
                appId,
                taskId,
                workspacePath,
                CodeGenerationCommandRunner.LogMode.SUMMARY,
                "lint"
        );
    }

    public CodeGenerationCommandResult runBuild(Long appId, Long taskId, Path workspacePath) {
        // 生成预览需要保留 JSX 插桩元数据，正式部署会单独执行 pnpm build。
        return commandRunner.runPnpmCommandResult(
                appId,
                taskId,
                workspacePath,
                CodeGenerationCommandRunner.LogMode.SUMMARY,
                "build:preview"
        );
    }
}
