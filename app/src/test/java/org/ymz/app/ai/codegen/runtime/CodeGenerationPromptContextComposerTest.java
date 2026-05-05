package org.ymz.app.ai.codegen.runtime;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.model.enums.codegen.CodeGenerationScenario;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGenerationPromptContextComposerTest {

    @TempDir
    Path workspacePath;

    @Test
    void composeIncludesFilesAndRepairContext() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "export default function IndexPage() {}");

        CodeGenerationContext context = CodeGenerationContext.builder()
                .appId(1L)
                .taskId(2L)
                .appName("测试应用")
                .workspacePath(workspacePath)
                .scenario(CodeGenerationScenario.REPAIR)
                .taskPrompt("修复 lint 报错")
                .repairAttempt(2)
                .failedCommand(CodeGenerationCommandResult.builder()
                        .commandText("pnpm.cmd lint")
                        .content("Unexpected unused var")
                        .exitCode(1)
                        .build())
                .build();
        InvocationContext invocationContext = InvocationContext.builder()
                .invocationParameters(InvocationParameters.from(Map.of("codegenContext", context)))
                .build();

        String prompt = new CodeGenerationPromptContextComposer().compose("base prompt", invocationContext);

        assertTrue(prompt.contains("测试应用"));
        assertTrue(prompt.contains("src/pages/IndexPage.jsx"));
        assertTrue(prompt.contains("自动修复轮次：第 2 轮"));
        assertTrue(prompt.contains("pnpm.cmd lint"));
        assertTrue(prompt.contains("Unexpected unused var"));
        assertTrue(prompt.contains("replaceInFile"));
        assertTrue(prompt.contains("只能调用这些工具"));
        assertTrue(prompt.contains("小范围修改优先使用 replaceInFile"));
    }
}
