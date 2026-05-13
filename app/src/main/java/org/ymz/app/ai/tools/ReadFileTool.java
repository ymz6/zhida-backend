package org.ymz.app.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ymz.app.config.AppPathProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 读取文本文件内容工具
 *
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadFileTool implements BaseTool {

    private final AppPathProperties appPathProperties;

    private static final Set<String> TEXT_CONTENT_TYPES = Set.of(
            "application/json",
            "application/javascript",
            "application/xml",
            "image/svg+xml");

    @Override
    public String toolName() {
        return "readFile";
    }

    @Override
    public String displayName() {
        return "读取文件";
    }

    @Override
    public String formatRequest(JSONObject arguments) {
        return "\n\n【选择工具】读取文件：`%s`\n".formatted(arguments.getStr("relativeFilePath", ""));
    }

    @Override
    public String formatResponse(JSONObject arguments, String result) {
        // 读取结果只展示摘要，避免把完整文件内容刷进聊天记录。
        return "\n【工具调用结果】已读取文件：`%s`（%d 行，%d 字符）\n\n".formatted(
                arguments.getStr("relativeFilePath", ""),
                result.lines().count(),
                result.length());
    }

    @Tool("读取文本文件内容")
    public String readFile(@P("相对源码根目录，例如：App.jsx、components/Button.jsx") String relativeFilePath, @ToolMemoryId Long appId) {
        log.debug("AI 调用读取文件工具， 请求参数：relativeFilePath={}, appId={}", relativeFilePath, appId);
        try {
            if (StrUtil.isBlank(relativeFilePath) || appId == null) {
                log.warn("AI 调用读取文件工具失败");
                return "读取文件失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }

            String normalizedRelativePath = FileUtil.normalize(relativeFilePath.trim());
            Path sourceRoot = appPathProperties.getTmpDir()
                    .resolve("app-workspace")
                    .resolve(String.valueOf(appId))
                    .resolve("src")
                    .normalize();
            Path targetPath = sourceRoot.resolve(Paths.get(normalizedRelativePath)).normalize();
            // 最终路径必须仍落在 src 内，避免 AI 访问工作区外文件。
            if (!targetPath.startsWith(sourceRoot)) {
                log.warn("AI 调用读取文件工具失败");
                return "读取文件失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }
            if (!Files.exists(targetPath)) {
                log.warn("AI 调用读取文件工具失败");
                return "读取文件失败: " + relativeFilePath + ", 错误: 文件不存在";
            }
            if (Files.isDirectory(targetPath)) {
                log.warn("AI 调用读取文件工具失败");
                return "读取文件失败: " + relativeFilePath + ", 错误: 不允许读取目录";
            }

            String contentType = Files.probeContentType(targetPath);
            // 只依赖本机文件元数据判断文件类型，不读取内容来猜测是否为二进制。
            if (contentType != null
                    && !contentType.startsWith("text/")
                    && !TEXT_CONTENT_TYPES.contains(contentType)) {
                log.warn("AI 调用读取文件工具失败");
                return "读取文件失败: " + relativeFilePath + ", 错误: 不支持读取二进制文件";
            }

            String content = Files.readString(targetPath, StandardCharsets.UTF_8);
            log.debug("AI 调用读取文件工具成功");
            return content;
        } catch (InvalidPathException | IOException e) {
            String errResult = "读取文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.warn("AI 调用读取文件工具失败", e);
            return errResult;
        }
    }

//    @Override
//    public String formatResponse(JSONObject arguments) {
//        String relativeFilePath = arguments.getStr("relativeFilePath", "");
//        return "\n\n[读取文件] " + relativeFilePath + "\n\n";
//    }
}
