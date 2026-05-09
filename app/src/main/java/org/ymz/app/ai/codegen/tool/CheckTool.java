package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.Tool;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;

import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.MAX_CHECK_LOG_CHARS;

/**
 * 运行 lint 校验。
 *
 * @author ymz
 */
public class CheckTool {

    private final WorkspaceToolSession session;

    public CheckTool(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool(name = "check", value = "运行 pnpm lint。无参数。通过后才能调用 build。")
    public String check() {
        CodeGenerationCommandResult result = session.runLint();
        if (!result.isSuccess()) {
            session.markLintFailed();
            throw new IllegalStateException("""
                    pnpm lint 未通过，请根据日志修复后再次调用 check。

                    %s
                    """.formatted(preview(result.getContent(), MAX_CHECK_LOG_CHARS)));
        }
        session.markLintPassed();
        return "pnpm lint 已通过，可以继续调用 build。";
    }

    private String preview(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content == null ? "" : content;
        }
        return content.substring(0, maxChars) + "\n...[truncated]";
    }
}
