package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.MAX_FILE_READ_BYTES;
import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.MAX_FILE_WRITE_CHARS;

/**
 * 精确替换工作区文件内容。
 *
 * @author ymz
 */
public class FileEditTool {

    private final WorkspaceToolSession session;

    public FileEditTool(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool(name = "editFile", value = "精确替换工作区文本文件中的唯一一段内容。适合小范围增量修改，调用前必须先 readFile。")
    public String editFile(
            @P(name = "path", description = "相对于项目根目录的文件路径。") String path,
            @P(name = "oldText", description = "文件中必须唯一匹配的原始文本。") String oldText,
            @P(name = "newText", description = "替换后的文本。删除内容时传空字符串。") String newText
    ) throws IOException {
        if (oldText == null || oldText.isEmpty()) {
            throw new IllegalArgumentException("被替换内容不能为空：" + path);
        }
        Path file = session.resolveWritePath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在：" + path);
        }
        if (Files.size(file) > MAX_FILE_READ_BYTES) {
            throw new IllegalArgumentException("文件过大，无法替换：" + path);
        }
        session.requireRead(file);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        int occurrences = countOccurrences(content, oldText);
        if (occurrences == 0) {
            throw new IllegalArgumentException("未找到要替换的内容：" + path);
        }
        if (occurrences > 1) {
            throw new IllegalArgumentException("要替换的内容匹配不唯一：" + path);
        }

        String replacement = newText == null ? "" : newText;
        String replaced = content.replace(oldText, replacement);
        if (replaced.length() > MAX_FILE_WRITE_CHARS) {
            throw new IllegalArgumentException("替换后文件内容过大：" + path);
        }
        Files.writeString(file, replaced, StandardCharsets.UTF_8);
        session.markWritten(file);
        return "已替换 " + session.toRelative(file) + "\n项目代码已变更，finish 前必须重新调用 check 和 build。";
    }

    private int countOccurrences(String content, String query) {
        int count = 0;
        int fromIndex = 0;
        while (fromIndex < content.length()) {
            int index = content.indexOf(query, fromIndex);
            if (index < 0) {
                break;
            }
            count++;
            fromIndex = index + 1;
        }
        return count;
    }
}
