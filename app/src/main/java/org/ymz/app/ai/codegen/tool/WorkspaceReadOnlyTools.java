package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;

/**
 * 暴露给对话答疑场景的只读工作区工具。
 *
 * @author ymz
 */
public class WorkspaceReadOnlyTools {

    private final WorkspaceToolSession session;

    public WorkspaceReadOnlyTools(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool("列出工作区目录下的文件。可选参数：directory。")
    public String listFiles(@P("相对于项目根目录的目录。") String directory) throws IOException {
        return session.listFiles(directory);
    }

    @Tool("读取工作区内的 UTF-8 文本文件。")
    public String readFile(@P("相对于项目根目录的文件路径。") String path) throws IOException {
        return session.readFile(path);
    }

    @Tool("在工作区文本文件中搜索指定字面量。")
    public String searchFiles(
            @P("要搜索的字面量文本。") String query,
            @P("可选，相对于项目根目录的目录。") String directory
    ) throws IOException {
        return session.searchFiles(query, directory);
    }
}
