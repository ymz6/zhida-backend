package org.ymz.app.ai.codegen.tool;
import lombok.Getter;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.ai.codegen.workspace.CodeGenerationProjectVerifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 单次工具调用会话。
 *
 * @author ymz
 */
public class WorkspaceToolSession {

    private static final int MAX_READ_BYTES = 200_000;
    private static final int MAX_WRITE_CHARS = 600_000;
    private static final int MAX_LIST_ITEMS = 300;
    private static final int MAX_SEARCH_MATCHES = 60;
    private static final int MAX_CHECK_LOG_CHARS = 12_000;
    private static final long SEARCH_TIMEOUT_SECONDS = 10;

    private static final Set<String> PROTECTED_EXACT_PATHS = Set.of(
            "package.json",
            "pnpm-lock.yaml",
            "vite.config.js",
            "eslint.config.js",
            "jsconfig.json",
            "components.json",
            "index.html",
            "src/App.jsx",
            "src/main.jsx",
            "src/lib/utils.js"
    );

    private final Path workspacePath;
    private final CodeGenerationProjectVerifier projectVerifier;
    private final Long appId;
    private final Long taskId;

    private boolean projectChecked;
    @Getter
    private String finalSummary;

    public WorkspaceToolSession(
            Path workspacePath,
            CodeGenerationProjectVerifier projectVerifier,
            Long appId,
            Long taskId
    ) {
        this.workspacePath = workspacePath.toAbsolutePath().normalize();
        this.projectVerifier = projectVerifier;
        this.appId = appId;
        this.taskId = taskId;
    }

    public String listFiles(String directory) throws IOException {
        Path root = resolveReadPath(directory == null || directory.isBlank() ? "." : directory);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("不是目录：" + directory);
        }

        List<String> lines = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, 6)) {
            for (Path path : stream
                    .filter(path -> !path.equals(root))
                    .filter(path -> !isIgnored(path))
                    .limit(MAX_LIST_ITEMS)
                    .toList()) {
                String relativePath = toRelative(path);
                lines.add(Files.isDirectory(path) ? relativePath + "/" : relativePath);
            }
        }
        return String.join("\n", lines);
    }

    public String readFile(String path) throws IOException {
        Path file = resolveReadPath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在：" + path);
        }
        if (Files.size(file) > MAX_READ_BYTES) {
            throw new IllegalArgumentException("文件过大，无法读取：" + path);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public String writeFile(String path, String content) throws IOException {
        if (content.length() > MAX_WRITE_CHARS) {
            throw new IllegalArgumentException("文件内容过大：" + path);
        }
        Path file = resolveWritePath(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        projectChecked = false;
        return "已写入 " + toRelative(file) + "\n项目代码已变更，finish 前必须重新调用 checkProject。";
    }

    public String replaceInFile(String path, String oldText, String newText) throws IOException {
        if (oldText == null || oldText.isEmpty()) {
            throw new IllegalArgumentException("被替换内容不能为空：" + path);
        }
        Path file = resolveWritePath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在：" + path);
        }
        if (Files.size(file) > MAX_READ_BYTES) {
            throw new IllegalArgumentException("文件过大，无法替换：" + path);
        }

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
        if (replaced.length() > MAX_WRITE_CHARS) {
            throw new IllegalArgumentException("替换后文件内容过大：" + path);
        }
        Files.writeString(file, replaced, StandardCharsets.UTF_8);
        projectChecked = false;
        return "已替换 " + toRelative(file) + "\n项目代码已变更，finish 前必须重新调用 checkProject。";
    }

    public String deleteFile(String path) throws IOException {
        Path file = resolveWritePath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("只能删除文件：" + path);
        }
        Files.delete(file);
        projectChecked = false;
        return "已删除 " + toRelative(file) + "\n项目代码已变更，finish 前必须重新调用 checkProject。";
    }

    public String searchFiles(String query, String directory) throws IOException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("搜索关键字不能为空");
        }
        Path root = resolveReadPath(directory == null || directory.isBlank() ? "." : directory);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("不是目录：" + directory);
        }
        try {
            return searchFilesWithRipgrep(query, root);
        } catch (IOException e) {
            return searchFilesWithJava(query, root);
        }
    }

    public String checkProject() {
        if (projectVerifier == null || appId == null || taskId == null) {
            projectChecked = false;
            throw new IllegalStateException("当前环境未配置项目校验工具，无法运行 checkProject");
        }

        CodeGenerationCommandResult failedCommand = projectVerifier.verify(appId, taskId, workspacePath);
        if (failedCommand != null) {
            projectChecked = false;
            String commandText = verificationCommandName(failedCommand);
            throw new IllegalStateException("""
                    %s 未通过，请根据日志修复后再次调用 checkProject。

                    %s
                    """.formatted(commandText, preview(failedCommand.getContent(), MAX_CHECK_LOG_CHARS)));
        }

        projectChecked = true;
        return "pnpm lint 和 pnpm build:preview 已通过，可以调用 finish。";
    }

    public String finish(String summary) {
        if (!projectChecked) {
            throw new IllegalStateException("最近一次文件修改后尚未通过 checkProject。请先调用 checkProject，并在 lint/build:preview 全部通过后再调用 finish。");
        }
        finalSummary = summary;
        return summary;
    }

    private String verificationCommandName(CodeGenerationCommandResult failedCommand) {
        String commandText = failedCommand.getCommandText();
        if (commandText == null || commandText.isBlank()) {
            return "项目校验";
        }
        if (commandText.contains("lint")) {
            return "pnpm lint";
        }
        if (commandText.contains("build:preview")) {
            return "pnpm build:preview";
        }
        if (commandText.contains("build")) {
            return "pnpm build";
        }
        return commandText;
    }

    private String searchFilesWithRipgrep(String query, Path root) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(isWindows() ? "rg.exe" : "rg");
        command.add("--fixed-strings");
        command.add("--line-number");
        command.add("--no-heading");
        command.add("--color=never");
        command.add("--path-separator");
        command.add("/");
        command.add("--max-filesize");
        command.add("200K");
        command.add("--glob");
        command.add("!node_modules/**");
        command.add("--glob");
        command.add("!dist/**");
        command.add("--glob");
        command.add("!.git/**");
        command.add("--");
        command.add(query);
        command.add(rgTarget(root));

        Process process = new ProcessBuilder(command)
                .directory(workspacePath.toFile())
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
                return searchFilesWithJava(query, root);
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

    private String searchFilesWithJava(String query, Path root) throws IOException {
        List<String> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            Iterator<Path> iterator = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(path))
                    .filter(this::isTextFile)
                    .iterator();
            while (iterator.hasNext() && matches.size() < MAX_SEARCH_MATCHES) {
                Path file = iterator.next();
                collectJavaMatches(query, file, matches);
            }
        }
        return searchResult(matches);
    }

    private void collectJavaMatches(String query, Path file, List<String> matches) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            Iterator<String> iterator = lines.iterator();
            int lineNumber = 1;
            while (iterator.hasNext() && matches.size() < MAX_SEARCH_MATCHES) {
                String line = iterator.next();
                if (line.contains(query)) {
                    matches.add(toRelative(file) + ":" + lineNumber + " " + line.trim());
                }
                lineNumber++;
            }
        }
    }

    private String searchResult(List<String> matches) {
        return matches.isEmpty() ? "未找到匹配内容" : String.join("\n", matches);
    }

    private String rgTarget(Path root) {
        String relativePath = toRelative(root);
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

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private Path resolveReadPath(String path) {
        Path resolved = resolvePath(path);
        if (isIgnored(resolved)) {
            throw new IllegalArgumentException("不允许访问该路径：" + path);
        }
        return resolved;
    }

    private Path resolveWritePath(String path) {
        Path resolved = resolvePath(path);
        if (isIgnored(resolved) || isProtected(resolved)) {
            throw new IllegalArgumentException("不允许修改该路径：" + path);
        }
        return resolved;
    }

    private Path resolvePath(String path) {
        String actual = path == null || path.isBlank() ? "." : path;
        Path relativePath = Path.of(actual);
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("必须使用相对路径：" + actual);
        }
        Path resolved = workspacePath.resolve(relativePath).normalize();
        if (!resolved.startsWith(workspacePath)) {
            throw new IllegalArgumentException("路径越界：" + actual);
        }
        return resolved;
    }

    private boolean isIgnored(Path path) {
        String relativePath = toRelative(path);
        return relativePath.equals("node_modules")
                || relativePath.startsWith("node_modules/")
                || relativePath.equals("dist")
                || relativePath.startsWith("dist/")
                || relativePath.equals(".git")
                || relativePath.startsWith(".git/");
    }

    private boolean isProtected(Path path) {
        String relativePath = toRelative(path);
        return PROTECTED_EXACT_PATHS.contains(relativePath)
                || relativePath.equals("src/components/ui")
                || relativePath.startsWith("src/components/ui/");
    }

    private boolean isTextFile(Path path) {
        try {
            return Files.size(path) <= MAX_READ_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    private String toRelative(Path path) {
        return workspacePath.relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private String preview(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content == null ? "" : content;
        }
        return content.substring(0, maxChars) + "\n...[truncated]";
    }
}
