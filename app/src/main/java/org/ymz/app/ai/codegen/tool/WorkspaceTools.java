package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;

/**
 * 暴露给 LLM 的工作区工具。
 *
 * @author ymz
 */
public class WorkspaceTools {

    private final WorkspaceToolSession session;

    public WorkspaceTools(WorkspaceToolSession session) {
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

    @Tool("在工作区内创建或覆盖 UTF-8 文本文件。")
    public String writeFile(
            @P("相对于项目根目录的文件路径。") String path,
            @P("完整文件内容。") String content
    ) throws IOException {
        return session.writeFile(path, content);
    }

    @Tool(name = "replaceInFile", value = "精确替换工作区文本文件中的唯一一段内容。适合小范围增量修改。")
    public String replaceInFile(
            @P(name = "path", description = "相对于项目根目录的文件路径。") String path,
            @P(name = "oldText", description = "文件中必须唯一匹配的原始文本。") String oldText,
            @P(name = "newText", description = "替换后的文本。删除内容时传空字符串。") String newText
    ) throws IOException {
        return session.replaceInFile(path, oldText, newText);
    }

    @Tool("删除工作区内的单个文件，不接受目录。")
    public String deleteFile(@P("相对于项目根目录的文件路径。") String path) throws IOException {
        return session.deleteFile(path);
    }

    @Tool("在工作区文本文件中搜索指定字面量。")
    public String searchFiles(
            @P("要搜索的字面量文本。") String query,
            @P("可选，相对于项目根目录的目录。") String directory
    ) throws IOException {
        return session.searchFiles(query, directory);
    }

    @Tool("运行固定项目校验：先执行 pnpm lint，通过后执行 pnpm build:preview。无参数。")
    public String checkProject() {
        return session.checkProject();
    }

    @Tool("当所有代码修改完成后调用，并提供简短总结。")
    public String finish(@P("已完成修改的简短总结。") String summary) {
        return session.finish(summary);
    }
}
