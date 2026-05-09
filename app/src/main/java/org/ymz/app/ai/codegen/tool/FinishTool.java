package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 标记本轮工具调用完成。
 *
 * @author ymz
 */
public class FinishTool {

    private final WorkspaceToolSession session;

    public FinishTool(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool(name = "finish", value = "当所有代码修改完成，且 check 和 build 均已通过后调用，并提供简短总结。")
    public String finish(@P(name = "summary", description = "已完成修改的简短总结。") String summary) {
        session.requireFinishReady();
        return session.setFinalSummary(summary);
    }
}
