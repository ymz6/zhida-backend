package org.ymz.app.ai.codegen.runtime;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules;
import org.ymz.app.model.enums.codegen.CodeGenerationScenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 将动态工作区上下文注入系统提示词。
 *
 * @author ymz
 */
@Component
public class CodeGenerationPromptContextComposer {

    public String compose(String basePrompt, InvocationContext invocationContext) {
        InvocationParameters parameters = invocationContext.invocationParameters();
        if (parameters == null) {
            return basePrompt;
        }
        CodeGenerationContext context = parameters.get("codegenContext");
        if (context == null) {
            return basePrompt;
        }

        StringBuilder prompt = new StringBuilder(StrUtil.blankToDefault(basePrompt, ""));
        prompt.append("\n\n## 当前调用上下文\n");
        prompt.append("应用名称：").append(StrUtil.blankToDefault(context.getAppName(), "未命名应用")).append('\n');
        prompt.append("场景：").append(context.getScenario().name()).append('\n');
        prompt.append("用户输入：\n").append(StrUtil.blankToDefault(context.getTaskPrompt(), "")).append('\n');
        prompt.append("当前项目文件：\n").append(describeCurrentFiles(context.getWorkspacePath())).append('\n');
        if (context.isRepair()) {
            appendRepairContext(prompt, context.getFailedCommand(), context.getRepairAttempt());
        }
        if (context.getScenario() == CodeGenerationScenario.CHAT) {
            prompt.append("""

                    ## 可用工具
                    - 仅限只读工具：readFile、glob、grep。
                    - 严禁尝试调用 writeFile、editFile、deleteFile、check、build、finish——它们在本轮不存在。

                    ## 过程输出要求
                    - 用中文输出简洁、有条理的回答。
                    - 涉及现有实现时，先用工具确认事实再回答。
                    - 不要暴露内部思维链。
                    """);
        } else {
            prompt.append("""

                    ## 可用工具与编辑策略
                    - 只能调用这些工具：readFile、writeFile、editFile、deleteFile、glob、grep、check、build、finish。
                    - 小范围修改优先使用 editFile，并确保 oldText 是文件中唯一匹配的完整原文片段。
                    - editFile 必须先 readFile；writeFile 覆盖已有文件也必须先 readFile，新建文件无需读取。
                    - 只有在需要新增文件、覆盖完整文件或大范围重写时，才使用 writeFile。
                    - 修改完成后必须先调用 check，再调用 build，二者都通过后才能调用 finish。
                    - 禁止调用未列出的工具名；如果工具调用失败，先读取文件重新定位，再选择正确工具继续。

                    ## 过程输出要求
                    - 用中文输出简短、高信息密度的进展说明。
                    - 进展说明要说明当前发现、准备执行的动作或修复方向，不要输出内部思维链。
                    - 关键修改完成后，必须调用 finish 工具总结本轮结果。
                    """);
        }
        return prompt.toString();
    }

    private void appendRepairContext(StringBuilder prompt, CodeGenerationCommandResult failedCommand, Integer repairAttempt) {
        prompt.append("自动修复轮次：第 ")
                .append(repairAttempt == null ? 1 : repairAttempt)
                .append(" 轮\n");
        if (failedCommand == null) {
            return;
        }
        prompt.append("失败命令：\n")
                .append(StrUtil.blankToDefault(failedCommand.getCommandText(), "未知命令"))
                .append('\n');
        prompt.append("退出码：\n")
                .append(failedCommand.getExitCode() == null ? "未知" : failedCommand.getExitCode())
                .append('\n');
        prompt.append("错误日志：\n")
                .append(StrUtil.blankToDefault(failedCommand.getContent(), "无"))
                .append('\n');
    }

    private String describeCurrentFiles(Path workspacePath) {
        if (workspacePath == null) {
            return "无法读取当前文件树：工作区不存在";
        }
        Path root = workspacePath.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return "无法读取当前文件树：工作区不存在";
        }

        List<String> lines = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, 6)) {
            for (Path path : stream
                    .filter(path -> !path.equals(root))
                    .filter(path -> !CodeGenerationWorkspaceRules.isIgnored(root, path))
                    .limit(CodeGenerationWorkspaceRules.MAX_TOOL_LIST_ITEMS)
                    .toList()) {
                String relativePath = CodeGenerationWorkspaceRules.toRelative(root, path);
                lines.add(Files.isDirectory(path) ? relativePath + "/" : relativePath);
            }
            return String.join("\n", lines);
        } catch (IOException e) {
            return "无法读取当前文件树：" + e.getMessage();
        }
    }

}
