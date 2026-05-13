package org.ymz.app.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件编辑工具
 * 对文件内容增量更新
 * 
 * @author ymz
 */
@Slf4j
@Component
public class EditFileTool implements BaseTool {

    // 警告提示词
    private static final String PROTECTED_EDIT_WARNING = """
            当前编辑文件请求已被拒绝，原因是以下文件属于受保护路径，禁止删除或修改：
            %s

            请明确回复用户：说明该路径受模板边界保护，严禁修改
            """.formatted(ProtectedTemplatePaths.listText());

    @Override
    public String toolName() {
        return "editFile";
    }

    @Override
    public String displayName() {
        return "编辑文件";
    }

    @Tool("替换文件中的一段内容")
    public String editFile(@P("相对源码根目录，例如：pages/Home.jsx、components/Button.jsx") String relativeFilePath, @P("要替换的旧内容，原样复制并尽量唯一") String oldContent, @P("替换后的新内容") String newContent, @ToolMemoryId Long appId) {
        log.debug("AI 调用编辑文件工具， 请求参数：relativeFilePath={}, oldContentLength={}, newContentLength={}, appId={}", relativeFilePath, oldContent == null ? 0 : oldContent.length(), newContent == null ? 0 : newContent.length(), appId);
        try {
            if (StrUtil.isBlank(relativeFilePath) || StrUtil.isEmpty(oldContent) || appId == null) {
                log.warn("AI 调用编辑文件工具失败");
                return "编辑文件失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }

            String normalizedRelativePath = FileUtil.normalize(relativeFilePath.trim());
            Path sourceRoot = Paths.get(System.getProperty("user.dir"), "tmp", "app-workspace", String.valueOf(appId), "src").normalize();
            Path targetPath = sourceRoot.resolve(Paths.get(normalizedRelativePath)).normalize();
            // 最终路径必须仍落在 src 内，避免 AI 访问工作区外文件。
            if (!targetPath.startsWith(sourceRoot)) {
                log.warn("AI 调用编辑文件工具失败");
                return "编辑文件失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }
            if (ProtectedTemplatePaths.contains(normalizedRelativePath)) {
                log.warn("AI 调用编辑文件工具失败");
                return PROTECTED_EDIT_WARNING;
            }

            if (!Files.exists(targetPath)) {
                log.warn("AI 调用编辑文件工具失败");
                return "编辑文件失败: " + relativeFilePath + ", 错误: 文件不存在";
            }
            if (Files.isDirectory(targetPath)) {
                log.warn("AI 调用编辑文件工具失败");
                return "编辑文件失败: " + relativeFilePath + ", 错误: 不允许编辑目录";
            }

            String content = Files.readString(targetPath, StandardCharsets.UTF_8);
            int index = content.indexOf(oldContent);
            if (index < 0) {
                log.warn("AI 调用编辑文件工具失败");
                return "编辑文件失败: " + relativeFilePath + ", 错误: 未找到要替换的内容";
            }

            // 仅替换第一次命中的内容，避免相同片段多处出现时扩大修改范围。
            String finalNewContent = newContent == null ? "" : newContent;
            String updatedContent = content.substring(0, index) + finalNewContent
                    + content.substring(index + oldContent.length());
            Files.writeString(targetPath, updatedContent, StandardCharsets.UTF_8);
            log.debug("AI 调用编辑文件工具成功");
            return normalizedRelativePath + " 已编辑";
        } catch (InvalidPathException | IOException e) {
            String errResult = "编辑文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.warn("AI 调用编辑文件工具失败", e);
            return errResult;
        }
    }

    @Override
    public String formatResponse(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath", "");
        String oldContent = arguments.getStr("oldContent", "");
        String newContent = arguments.getStr("newContent", "");
        // 显示对比内容
        return String.format("""
                [工具调用] %s %s

                替换前：
                ```
                %s
                ```

                替换后：
                ```
                %s
                ```
                """, displayName(), relativeFilePath, oldContent, newContent);
    }
}
