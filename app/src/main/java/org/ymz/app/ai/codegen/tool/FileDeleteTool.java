package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 删除工作区文件。
 *
 * @author ymz
 */
public class FileDeleteTool {

    private final WorkspaceToolSession session;

    public FileDeleteTool(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool(name = "deleteFile", value = "删除工作区内的单个文件，不接受目录。")
    public String deleteFile(@P(name = "path", description = "相对于项目根目录的文件路径。") String path) throws IOException {
        Path file = session.resolveWritePath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("只能删除文件：" + path);
        }
        Files.delete(file);
        session.markWritten(file);
        return "已删除 " + session.toRelative(file) + "\n项目代码已变更，finish 前必须重新调用 check 和 build。";
    }
}
