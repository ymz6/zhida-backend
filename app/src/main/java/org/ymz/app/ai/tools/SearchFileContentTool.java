package org.ymz.app.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 根据文件内容搜索相关文件
 *
 * @author ymz
 */
@Slf4j
@Component
public class SearchFileContentTool implements BaseTool {

    private static final int MAX_RESULTS = 50;

    @Override
    public String toolName() {
        return "searchFilesByContent";
    }

    @Override
    public String displayName() {
        return "搜索文件内容";
    }

    @Tool("根据关键词搜索文本文件内容")
    public String searchFilesByContent(@P("文件内容关键词，例如：Submit、useState、className") String keyword, @ToolMemoryId Long appId) {
        log.debug("AI 调用搜索文件内容工具， 请求参数：keyword={}, appId={}", keyword, appId);
        try {
            if (StrUtil.isBlank(keyword) || appId == null) {
                log.warn("AI 调用搜索文件内容工具失败");
                return "搜索文件内容失败: " + keyword + ", 错误: 非法的搜索关键词";
            }

            // 文件搜索限定在虚拟源码根目录内，避免 AI 接触工程底座文件。
            Path sourcePath = Paths.get(System.getProperty("user.dir"), "tmp", "app-workspace", String.valueOf(appId), "src").normalize();
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                log.warn("AI 调用搜索文件内容工具失败");
                return "搜索文件内容失败: " + keyword + ", 错误: 源码目录不存在";
            }

            List<String> results = new ArrayList<>();
            // 搜索按大小写不敏感处理，降低 AI 调用工具时的关键词精度要求。
            String lowerKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // 逐行读取，命中足够结果后立即停止，避免一次性加载大文件。
                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        String line;
                        int lineNumber = 0;
                        while ((line = reader.readLine()) != null) {
                            lineNumber++;
                            if (line.toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                                // 返回给 AI 的路径使用源码根目录下的虚拟相对路径。
                                String relativePath = sourcePath.relativize(file).toString().replace("\\", "/");
                                results.add(relativePath + ":" + lineNumber + ":" + line);
                                // 命中足够多时直接停止遍历，避免一次搜索占用过多时间。
                                if (results.size() >= MAX_RESULTS) {
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        // 读取失败的文件按非文本文件跳过，不影响其它源码文件搜索。
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            log.debug("AI 调用搜索文件内容工具成功");
            return results.isEmpty() ? "未找到匹配内容" : results.stream().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            String errResult = "搜索文件内容失败: " + keyword + ", 错误: " + e.getMessage();
            log.warn("AI 调用搜索文件内容工具失败", e);
            return errResult;
        }
    }

    @Override
    public String formatResponse(JSONObject arguments) {
        String keyword = arguments.getStr("keyword", "");
        return "\n\n[搜索文件内容] " + keyword + "\n\n";
    }
}
