package org.ymz.app.ai.tools;

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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 根据文件名搜索相关文件
 *
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchFileNameTool implements BaseTool {

    private static final int MAX_RESULTS = 50;

    private final AppPathProperties appPathProperties;

    @Override
    public String toolName() {
        return "searchFilesByName";
    }

    @Override
    public String displayName() {
        return "搜索文件名";
    }

    @Override
    public String formatRequest(JSONObject arguments) {
        return BaseTool.toolCallTag(toolName(), displayName(),
                "搜索文件名：`%s`".formatted(arguments.getStr("keyword", "")));
    }

    @Override
    public String formatResponse(JSONObject arguments, String result) {
        if (result != null && result.startsWith("搜索文件名失败")) {
            return BaseTool.toolResultTag(toolName(), displayName(), false, result);
        }
        if ("未找到匹配文件".equals(result)) {
            return BaseTool.toolResultTag(toolName(), displayName(), true, result);
        }

        String content = """
                找到 %d 个匹配文件：
                ```text
                %s
                ```
                """.formatted(result.lines().count(), result);
        return BaseTool.toolResultTag(toolName(), displayName(), true, content);
    }

    @Tool("根据关键词模糊搜索文件名")
    public String searchFilesByName(@P("文件名关键词，例如：Button、App、style") String keyword, @ToolMemoryId Long appId) {
        log.debug("AI 调用搜索文件名工具， 请求参数：keyword={}, appId={}", keyword, appId);
        try {
            if (StrUtil.isBlank(keyword) || appId == null) {
                log.warn("AI 调用搜索文件名工具失败");
                return "搜索文件名失败: " + keyword + ", 错误: 非法的搜索关键词";
            }

            Path sourcePath = appPathProperties.getTmpDir()
                    .resolve("app-workspace")
                    .resolve(String.valueOf(appId))
                    .resolve("src")
                    .normalize();
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                log.warn("AI 调用搜索文件名工具失败");
                return "搜索文件名失败: " + keyword + ", 错误: 源码目录不存在";
            }

            List<String> results = new ArrayList<>();
            String lowerKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                        results.add(sourcePath.relativize(file).toString().replace("\\", "/"));
                    }
                    return results.size() >= MAX_RESULTS ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });

            log.debug("AI 调用搜索文件名工具成功");
            return results.isEmpty() ? "未找到匹配文件" : results.stream().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            String errResult = "搜索文件名失败: " + keyword + ", 错误: " + e.getMessage();
            log.warn("AI 调用搜索文件名工具失败", e);
            return errResult;
        }
    }

}
