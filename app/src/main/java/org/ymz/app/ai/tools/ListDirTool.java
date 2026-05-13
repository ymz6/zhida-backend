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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 列出目录下的所有文件、目录
 * @author ymz
 */
@Slf4j
@Component
public class ListDirTool implements BaseTool{
    @Override
    public String toolName() {
        return "listDirectory";
    }

    @Tool("列出源码目录内的一层文件和子目录")
    public String listDirectory(@P("相对源码根目录，例如：.、pages、components；. 表示源码根目录") String relativeDirectoryPath, @ToolMemoryId Long appId) {
        log.debug("AI 调用列目录工具， 请求参数：relativeDirectoryPath={}, appId={}", relativeDirectoryPath, appId);
        try {
            if (StrUtil.isBlank(relativeDirectoryPath) || appId == null) {
                log.warn("AI 调用列目录工具失败");
                return "列出目录失败: " + relativeDirectoryPath + ", 错误: 非法的目录路径";
            }

            String normalizedRelativePath = FileUtil.normalize(relativeDirectoryPath.trim());
            // Hutool 会把 "." 规范化为空串，列目录时需要保留源码根目录语义。
            if (normalizedRelativePath.isBlank()) {
                normalizedRelativePath = ".";
            }
            Path sourceRoot = Paths.get(System.getProperty("user.dir"), "tmp", "app-workspace", String.valueOf(appId), "src").normalize();
            Path targetPath = sourceRoot.resolve(Paths.get(normalizedRelativePath)).normalize();
            // 最终路径必须仍落在 src 内，避免 AI 访问工作区外文件。
            if (!targetPath.startsWith(sourceRoot)) {
                log.warn("AI 调用列目录工具失败");
                return "列出目录失败: " + relativeDirectoryPath + ", 错误: 非法的目录路径";
            }
            if (!Files.exists(targetPath)) {
                log.warn("AI 调用列目录工具失败");
                return "列出目录失败: " + relativeDirectoryPath + ", 错误: 目录不存在";
            }
            if (!Files.isDirectory(targetPath)) {
                log.warn("AI 调用列目录工具失败");
                return "列出目录失败: " + relativeDirectoryPath + ", 错误: 不是目录";
            }

            String result;
            try (var stream = Files.list(targetPath)) {
                result = stream
                        .sorted(Comparator.comparing(item -> item.getFileName().toString()))
                        .map(item -> sourceRoot.relativize(item).toString().replace("\\", "/"))
                        .collect(Collectors.joining("\n"));
            }
            log.debug("AI 调用列目录工具成功");
            return StrUtil.isBlank(result) ? "目录为空" : result;
        } catch (InvalidPathException | IOException e) {
            String errResult = "列出目录失败: " + relativeDirectoryPath + ", 错误: " + e.getMessage();
            log.warn("AI 调用列目录工具失败", e);
            return errResult;
        }
    }
    @Override
    public String displayName() {
        return "列目录";
    }

    @Override
    public String formatResponse(JSONObject arguments) {
        String relativeDirectoryPath = arguments.getStr("relativeDirectoryPath", "");
        return "\n\n[列目录] " + relativeDirectoryPath + "\n\n";
    }
}
