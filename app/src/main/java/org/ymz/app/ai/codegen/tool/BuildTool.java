package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.Tool;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;

import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.MAX_CHECK_LOG_CHARS;

/**
 * 运行预览构建。
 *
 * @author ymz
 */
public class BuildTool {

    private final WorkspaceToolSession session;

    public BuildTool(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool(name = "build", value = "运行 pnpm build:preview。调用前必须先通过 check。无参数。")
    public String build() {
        session.requireLintPassed();
        CodeGenerationCommandResult result = session.runBuild();
        if (!result.isSuccess()) {
            session.markBuildFailed();
            throw new IllegalStateException("""
                    pnpm build:preview 未通过，请根据日志修复后再次调用 check 和 build。

                    %s
                    """.formatted(preview(result.getContent(), MAX_CHECK_LOG_CHARS)));
        }
        session.markBuildPassed();
        return "pnpm build:preview 已通过，可以调用 finish。";
    }

    private String preview(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content == null ? "" : content;
        }
        return content.substring(0, maxChars) + "\n...[truncated]";
    }
}
