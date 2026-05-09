package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.MAX_TOOL_LIST_ITEMS;

/**
 * 按 glob 模式查找工作区文件。
 *
 * @author ymz
 */
public class GlobTool {

    private final WorkspaceToolSession session;

    public GlobTool(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool(name = "glob", value = "按 glob 模式列出工作区文件，例如 **/*.jsx、src/components/**/*。")
    public String glob(@P(name = "pattern", description = "相对于项目根目录的 glob 模式。") String pattern) throws IOException {
        String actualPattern = normalizePattern(pattern);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + toSystemPattern(actualPattern));
        Path root = session.getWorkspacePath();
        List<String> lines = new ArrayList<>();
        boolean limitReached = false;

        try (Stream<Path> stream = Files.walk(root)) {
            Iterator<Path> iterator = stream
                    .filter(item -> !item.equals(root))
                    .filter(item -> !session.isIgnored(item))
                    .iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                Path relative = root.relativize(path);
                if (!matches(actualPattern, matcher, relative)) {
                    continue;
                }
                if (lines.size() >= MAX_TOOL_LIST_ITEMS) {
                    limitReached = true;
                    break;
                }
                String relativePath = session.toRelative(path);
                lines.add(Files.isDirectory(path) ? relativePath + "/" : relativePath);
            }
        }

        if (lines.isEmpty()) {
            return "未找到匹配文件";
        }
        if (limitReached) {
            lines.add("结果过多，请收窄 pattern。");
        }
        return String.join("\n", lines);
    }

    private String normalizePattern(String pattern) {
        String actual = pattern == null || pattern.isBlank() ? "**/*" : pattern.trim();
        while (actual.startsWith("./")) {
            actual = actual.substring(2);
        }
        return actual.replace('\\', '/');
    }

    private String toSystemPattern(String pattern) {
        String separator = FileSystems.getDefault().getSeparator();
        return "/".equals(separator) ? pattern : pattern.replace("/", separator);
    }

    private boolean matches(String pattern, PathMatcher matcher, Path relative) {
        // **/* 用于还原“列出全部文件”的语义，需要覆盖根目录下的文件。
        if ("**/*".equals(pattern)) {
            return true;
        }
        if (matcher.matches(relative)) {
            return true;
        }
        // Windows 下 PathMatcher 对用户常用的 / 分隔 glob 不够稳定，这里用统一路径格式兜底。
        String relativePath = relative.toString().replace('\\', '/');
        return Pattern.matches(toRegex(pattern), relativePath);
    }

    private String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (current == '*') {
                boolean doubleStar = i + 1 < pattern.length() && pattern.charAt(i + 1) == '*';
                if (doubleStar && i + 2 < pattern.length() && pattern.charAt(i + 2) == '/') {
                    regex.append("(?:.*/)?");
                    i += 2;
                } else if (doubleStar) {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
                continue;
            }
            if (current == '?') {
                regex.append("[^/]");
                continue;
            }
            if (".()[]{}+$^|".indexOf(current) >= 0) {
                regex.append('\\');
            }
            regex.append(current);
        }
        return regex.toString();
    }
}
