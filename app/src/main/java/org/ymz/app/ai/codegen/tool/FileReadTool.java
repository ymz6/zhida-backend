package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.MAX_FILE_READ_BYTES;

/**
 * 读取工作区文件。
 *
 * @author ymz
 */
public class FileReadTool {

    private final WorkspaceToolSession session;

    public FileReadTool(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool(name = "readFile", value = "读取工作区内的 UTF-8 文本文件。读取成功后才能编辑或覆盖已有文件。")
    public String readFile(@P(name = "path", description = "相对于项目根目录的文件路径。") String path) throws IOException {
        Path file = session.resolveReadPath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在：" + path);
        }
        if (Files.size(file) > MAX_FILE_READ_BYTES) {
            throw new IllegalArgumentException("文件过大，无法读取：" + path);
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        session.markRead(file);
        return content;
    }
}
