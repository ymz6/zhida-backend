package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.MAX_FILE_WRITE_CHARS;

/**
 * 创建或覆盖工作区文件。
 *
 * @author ymz
 */
public class FileWriteTool {

    private final WorkspaceToolSession session;

    public FileWriteTool(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool(name = "writeFile", value = "在工作区内创建或覆盖 UTF-8 文本文件。覆盖已有文件前必须先 readFile。")
    public String writeFile(
            @P(name = "path", description = "相对于项目根目录的文件路径。") String path,
            @P(name = "content", description = "完整文件内容。") String content
    ) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("文件内容不能为空：" + path);
        }
        if (content.length() > MAX_FILE_WRITE_CHARS) {
            throw new IllegalArgumentException("文件内容过大：" + path);
        }
        Path file = session.resolveWritePath(path);
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("不能覆盖目录：" + path);
            }
            // 覆盖已有文件属于破坏性操作，必须基于最新读取内容执行。
            session.requireRead(file);
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        session.markWritten(file);
        return "已写入 " + session.toRelative(file) + "\n项目代码已变更，finish 前必须重新调用 check 和 build。";
    }
}
