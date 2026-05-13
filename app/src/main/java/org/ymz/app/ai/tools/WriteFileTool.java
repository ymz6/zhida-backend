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
 * 写文件工具
 * 文件存在则覆盖原有内容，不存在则创建
 * 
 * @author ymz
 */
@Slf4j
@Component
public class WriteFileTool implements BaseTool {

    // 警告提示词
    private static final String PROTECTED_WRITE_WARNING = """
            当前写入文件请求已被拒绝，原因是以下文件属于受保护路径，禁止删除或修改：
            %s

            请明确回复用户：说明该路径受模板边界保护，严禁修改
            """.formatted(ProtectedTemplatePaths.listText());

    @Override
    public String toolName() {
        return "writeFile";
    }

    @Tool("写入文件")
    public String writeFile(@P("相对源码根目录，例如：pages/Home.jsx、components/Button.jsx") String relativeFilePath, @P("要写入文件的内容") String content, @ToolMemoryId Long appId) {
        log.debug("AI 调用写入文件工具， 请求参数：relativeFilePath={}, contentLength={}, appId={}", relativeFilePath, content == null ? 0 : content.length(), appId);
        try {
            if (StrUtil.isBlank(relativeFilePath) || appId == null) {
                log.warn("AI 调用写入文件工具失败");
                return "写入文件失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }

            String normalizedRelativePath = FileUtil.normalize(relativeFilePath.trim());
            Path sourceRoot = Paths.get(System.getProperty("user.dir"), "tmp", "app-workspace", String.valueOf(appId), "src").normalize();
            Path targetPath = sourceRoot.resolve(Paths.get(normalizedRelativePath)).normalize();
            // 最终路径必须仍落在 src 内，避免 AI 访问工作区外文件。
            if (!targetPath.startsWith(sourceRoot)) {
                log.warn("AI 调用写入文件工具失败");
                return "写入文件失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }
            if (ProtectedTemplatePaths.contains(normalizedRelativePath)) {
                log.warn("AI 调用写入文件工具失败");
                return PROTECTED_WRITE_WARNING;
            }

            if (Files.isDirectory(targetPath)) {
                log.warn("AI 调用写入文件工具失败");
                return "写入文件失败: " + relativeFilePath + ", 错误: 不允许写入目录";
            }

            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, content, StandardCharsets.UTF_8);
            log.debug("AI 调用写入文件工具成功");
            return normalizedRelativePath + " 已写入";
        } catch (InvalidPathException | IOException e) {
            String errResult = "写入文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.warn("AI 调用写入文件工具失败", e);
            return errResult;
        }
    }

    @Override
    public String displayName() {
        return "写文件";
    }

    @Override
    public String formatRequest(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath", "");
        return "\n\n[选择工具] 写文件：" + relativeFilePath + "\n\n";
    }

    @Override
    public String formatResponse(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath", "");
        return "\n\n[写文件] " + relativeFilePath + "\n\n";
    }
}
