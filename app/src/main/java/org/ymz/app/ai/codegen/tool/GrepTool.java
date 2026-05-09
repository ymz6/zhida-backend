package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.MAX_FILE_READ_BYTES;
import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.MAX_SEARCH_MATCHES;
import static org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules.SEARCH_TIMEOUT_SECONDS;

/**
 * 搜索工作区文本内容。
 *
 * @author ymz
 */
public class GrepTool {

    private final WorkspaceToolSession session;

    public GrepTool(WorkspaceToolSession session) {
        this.session = session;
    }

    @Tool(name = "grep", value = "在工作区文本文件中搜索内容，默认按字面量搜索；regex 为 true 时按正则搜索。")
    public String grep(
            @P(name = "query", description = "要搜索的文本或正则表达式。") String query,
            @P(name = "directory", description = "可选，相对于项目根目录的目录。", required = false) String directory,
            @P(name = "regex", description = "可选，是否按正则表达式搜索，默认 false。", required = false) Boolean regex
    ) throws IOException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("搜索关键字不能为空");
        }
        boolean useRegex = Boolean.TRUE.equals(regex);
        Pattern javaPattern = compilePattern(query, useRegex);
        Path root = session.resolveReadPath(directory == null || directory.isBlank() ? "." : directory);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("不是目录：" + directory);
        }
        try {
            return searchWithRipgrep(query, root, useRegex);
        } catch (IOException e) {
            return searchWithJava(query, javaPattern, root);
        }
    }

    private Pattern compilePattern(String query, boolean useRegex) {
        if (!useRegex) {
            return null;
        }
        try {
            return Pattern.compile(query);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("正则表达式不合法：" + e.getMessage());
        }
    }

    private String searchWithRipgrep(String query, Path root, boolean useRegex) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(isWindows() ? "rg.exe" : "rg");
        if (!useRegex) {
            command.add("--fixed-strings");
        }
        command.add("--line-number");
        command.add("--no-heading");
        command.add("--color=never");
        command.add("--path-separator");
        command.add("/");
        command.add("--max-filesize");
        command.add("200K");
        for (String ignoredGlob : CodeGenerationWorkspaceRules.ripgrepIgnoredGlobs()) {
            command.add("--glob");
            command.add(ignoredGlob);
        }
        command.add("--");
        command.add(query);
        command.add(rgTarget(root));

        Process process = new ProcessBuilder(command)
                .directory(session.getWorkspacePath().toFile())
                .redirectErrorStream(true)
                .start();

        List<String> matches = new ArrayList<>();
        boolean limitReached = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    matches.add(normalizeSearchLine(line));
                    if (matches.size() >= MAX_SEARCH_MATCHES) {
                        limitReached = true;
                        process.destroy();
                        break;
                    }
                }
            }
        }

        try {
            boolean completed = process.waitFor(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("搜索超时");
            }
            int exitCode = process.exitValue();
            if (!limitReached && exitCode > 1) {
                throw new IOException("ripgrep 搜索失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("搜索被中断", e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return searchResult(matches);
    }

    private String searchWithJava(String query, Pattern pattern, Path root) throws IOException {
        List<String> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            Iterator<Path> iterator = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !session.isIgnored(path))
                    .filter(this::isTextFile)
                    .iterator();
            while (iterator.hasNext() && matches.size() < MAX_SEARCH_MATCHES) {
                Path file = iterator.next();
                collectJavaMatches(query, pattern, file, matches);
            }
        }
        return searchResult(matches);
    }

    private void collectJavaMatches(String query, Pattern pattern, Path file, List<String> matches) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            Iterator<String> iterator = lines.iterator();
            int lineNumber = 1;
            while (iterator.hasNext() && matches.size() < MAX_SEARCH_MATCHES) {
                String line = iterator.next();
                boolean matched = pattern == null ? line.contains(query) : pattern.matcher(line).find();
                if (matched) {
                    matches.add(session.toRelative(file) + ":" + lineNumber + " " + line.trim());
                }
                lineNumber++;
            }
        }
    }

    private String searchResult(List<String> matches) {
        return matches.isEmpty() ? "未找到匹配内容" : String.join("\n", matches);
    }

    private String rgTarget(Path root) {
        String relativePath = session.toRelative(root);
        return relativePath.isBlank() ? "." : relativePath;
    }

    private String normalizeSearchLine(String line) {
        String normalized = line.replace('\\', '/');
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        int firstSeparator = normalized.indexOf(':');
        int secondSeparator = firstSeparator < 0 ? -1 : normalized.indexOf(':', firstSeparator + 1);
        if (firstSeparator > 0 && secondSeparator > firstSeparator + 1) {
            String lineNumber = normalized.substring(firstSeparator + 1, secondSeparator);
            if (lineNumber.chars().allMatch(Character::isDigit)) {
                return normalized.substring(0, firstSeparator)
                        + ":"
                        + lineNumber
                        + " "
                        + normalized.substring(secondSeparator + 1).trim();
            }
        }
        return normalized;
    }

    private boolean isTextFile(Path path) {
        try {
            return Files.size(path) <= MAX_FILE_READ_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
