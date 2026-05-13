package org.ymz.app.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件删除工具
 * 
 * @author ymz
 */
@Slf4j
@Component
public class DeleteFileTool implements BaseTool {

    // 警告提示词
    private static final String PROTECTED_DELETE_WARNING = """
            当前删除文件请求已被拒绝，原因是以下文件属于受保护路径，禁止删除或修改：
            %s

            请明确回复用户：说明该路径受模板边界保护，严禁删除
            """.formatted(ProtectedTemplatePaths.listText());

    @Tool("删除文件")
    public String deleteFile(@P("相对源码根目录，例如：pages/Home.jsx、components/Button.jsx") String relativeFilePath, @ToolMemoryId Long appId) {
        log.debug("AI 调用删除文件工具， 请求参数：relativeFilePath={}, appId={}", relativeFilePath, appId);
        try {
            if (relativeFilePath == null || relativeFilePath.isBlank() || appId == null) {
                log.warn("AI 调用删除文件工具失败");
                return "删除文件失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }

            String normalizedRelativePath = FileUtil.normalize(relativeFilePath.trim());
            Path sourceRoot = Paths.get(System.getProperty("user.dir"), "tmp", "app-workspace", String.valueOf(appId), "src").normalize();
            Path targetPath = sourceRoot.resolve(Paths.get(normalizedRelativePath)).normalize();
            // 最终路径必须仍落在 src 内，避免 AI 访问工作区外文件。
            if (!targetPath.startsWith(sourceRoot)) {
                log.warn("AI 调用删除文件工具失败");
                return "删除文件失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }
            if (ProtectedTemplatePaths.contains(normalizedRelativePath)) {
                log.warn("AI 调用删除文件工具失败");
                return PROTECTED_DELETE_WARNING;
            }

            if (!Files.exists(targetPath)) {
                log.warn("AI 调用删除文件工具失败");
                return "删除文件失败: " + relativeFilePath + ", 错误: 文件不存在";
            }
            if (Files.isDirectory(targetPath)) {
                log.warn("AI 调用删除文件工具失败");
                return "删除文件失败: " + relativeFilePath + ", 错误: 不允许删除目录";
            }

            Files.delete(targetPath);
            log.debug("AI 调用删除文件工具成功");
            return normalizedRelativePath + " 已删除";
        } catch (InvalidPathException | IOException e) {
            String errResult = "删除文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.warn("AI 调用删除文件工具失败", e);
            return errResult;
        }
    }

    @Override
    public String toolName() {
        return "deleteFile";
    }

    @Override
    public String displayName() {
        return "删除文件";
    }

    @Override
    public String formatRequest(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath", "");
        return "\n\n[选择工具] 删除文件：" + relativeFilePath + "\n\n";
    }

    @Override
    public String formatResponse(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath", "");
        return "\n\n[删除文件] " + relativeFilePath + "\n\n";
    }
}
