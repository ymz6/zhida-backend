package org.ymz.app.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.ymz.app.service.generation.ProjectCommandResult;
import org.ymz.app.service.generation.ProjectCommandRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 限制在单个应用工作区内的文件工具。
 *
 * @author ymz
 */
public class WorkspaceToolExecutor {

    private static final int MAX_READ_BYTES = 200_000;
    private static final int MAX_WRITE_CHARS = 600_000;
    private static final int MAX_LIST_ITEMS = 300;
    private static final int MAX_SEARCH_MATCHES = 60;
    private static final int MAX_CHECK_LOG_CHARS = 12000;
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
    private final ObjectMapper objectMapper;
    private final ProjectCommandRunner projectCommandRunner;
    private final Long appId;
    private final Long taskId;
    private boolean projectChecked;

    public WorkspaceToolExecutor(Path workspacePath, ObjectMapper objectMapper) {
        this(workspacePath, objectMapper, null, null, null);
    }

    public WorkspaceToolExecutor(
            Path workspacePath,
            ObjectMapper objectMapper,
            ProjectCommandRunner projectCommandRunner,
            Long appId,
            Long taskId
    ) {
        this.workspacePath = workspacePath.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.projectCommandRunner = projectCommandRunner;
        this.appId = appId;
        this.taskId = taskId;
    }

    public List<ToolSpecification> toolSpecifications() {
        return List.of(
                ToolSpecification.builder()
                        .name("listFiles")
                        .description("列出工作区目录下的文件。可选参数：directory。")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("directory", "相对于项目根目录的目录。")
                                .additionalProperties(false)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("readFile")
                        .description("读取工作区内的 UTF-8 文本文件。")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("path", "相对于项目根目录的文件路径。")
                                .required("path")
                                .additionalProperties(false)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("writeFile")
                        .description("在工作区内创建或覆盖 UTF-8 文本文件。")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("path", "相对于项目根目录的文件路径。")
                                .addStringProperty("content", "完整文件内容。")
                                .required("path", "content")
                                .additionalProperties(false)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("deleteFile")
                        .description("删除工作区内的单个文件，不接受目录。")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("path", "相对于项目根目录的文件路径。")
                                .required("path")
                                .additionalProperties(false)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("searchFiles")
                        .description("在工作区文本文件中搜索指定字面量。")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("query", "要搜索的字面量文本。")
                                .addStringProperty("directory", "可选，相对于项目根目录的目录。")
                                .required("query")
                                .additionalProperties(false)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("checkProject")
                        .description("运行固定项目校验：先执行 pnpm lint，通过后执行 pnpm build。无参数。")
                        .parameters(JsonObjectSchema.builder()
                                .additionalProperties(false)
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("finish")
                        .description("当所有代码修改完成后调用，并提供简短总结。")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("summary", "已完成修改的简短总结。")
                                .required("summary")
                                .additionalProperties(false)
                                .build())
                        .build()
        );
    }

    public WorkspaceToolResult execute(ToolExecutionRequest request) {
        try {
            Map<String, String> arguments = arguments(request.arguments());
            return switch (request.name()) {
                case "listFiles" -> ok(listFiles(arguments.get("directory")));
                case "readFile" -> ok(readFile(required(arguments, "path")));
                case "writeFile" -> writeFileResult(required(arguments, "path"), required(arguments, "content"));
                case "deleteFile" -> deleteFileResult(required(arguments, "path"));
                case "searchFiles" -> ok(searchFiles(required(arguments, "query"), arguments.get("directory")));
                case "checkProject" -> checkProject();
                case "finish" -> finish(required(arguments, "summary"));
                default -> error("未知工具：" + request.name());
            };
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    public String describeCurrentFiles() throws IOException {
        return listFiles(null);
    }

    private String listFiles(String directory) throws IOException {
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

    private String readFile(String path) throws IOException {
        Path file = resolveReadPath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在：" + path);
        }
        if (Files.size(file) > MAX_READ_BYTES) {
            throw new IllegalArgumentException("文件过大，无法读取：" + path);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private WorkspaceToolResult writeFileResult(String path, String content) throws IOException {
        String result = writeFile(path, content);
        projectChecked = false;
        return ok(result + "\n项目代码已变更，finish 前必须重新调用 checkProject。");
    }

    private String writeFile(String path, String content) throws IOException {
        if (content.length() > MAX_WRITE_CHARS) {
            throw new IllegalArgumentException("文件内容过大：" + path);
        }
        Path file = resolveWritePath(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return "已写入 " + toRelative(file);
    }

    private WorkspaceToolResult deleteFileResult(String path) throws IOException {
        String result = deleteFile(path);
        projectChecked = false;
        return ok(result + "\n项目代码已变更，finish 前必须重新调用 checkProject。");
    }

    private String deleteFile(String path) throws IOException {
        Path file = resolveWritePath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("只能删除文件：" + path);
        }
        Files.delete(file);
        return "已删除 " + toRelative(file);
    }

    private String searchFiles(String query, String directory) throws IOException {
        if (query.isBlank()) {
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

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private WorkspaceToolResult checkProject() {
        if (projectCommandRunner == null || appId == null || taskId == null) {
            projectChecked = false;
            return error("当前环境未配置项目校验工具，无法运行 checkProject");
        }

        ProjectCommandResult lintResult = projectCommandRunner.runPnpmCommandResult(
                appId,
                taskId,
                workspacePath,
                ProjectCommandRunner.LogMode.SUMMARY,
                "lint"
        );
        if (!lintResult.isSuccess()) {
            projectChecked = false;
            return error("""
                    pnpm lint 未通过，请根据日志修复后再次调用 checkProject。

                    %s
                    """.formatted(preview(lintResult.getContent(), MAX_CHECK_LOG_CHARS)));
        }

        ProjectCommandResult buildResult = projectCommandRunner.runPnpmCommandResult(
                appId,
                taskId,
                workspacePath,
                ProjectCommandRunner.LogMode.SUMMARY,
                "build"
        );
        if (!buildResult.isSuccess()) {
            projectChecked = false;
            return error("""
                    pnpm build 未通过，请根据日志修复后再次调用 checkProject。

                    %s
                    """.formatted(preview(buildResult.getContent(), MAX_CHECK_LOG_CHARS)));
        }

        projectChecked = true;
        return ok("pnpm lint 和 pnpm build 已通过，可以调用 finish。");
    }

    private WorkspaceToolResult finish(String summary) {
        if (!projectChecked) {
            return error("最近一次文件修改后尚未通过 checkProject。请先调用 checkProject，并在 lint/build 全部通过后再调用 finish。");
        }
        return WorkspaceToolResult.builder()
                .content(summary)
                .finished(true)
                .build();
    }

    private Map<String, String> arguments(String rawArguments) throws IOException {
        if (rawArguments == null || rawArguments.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(rawArguments, new TypeReference<>() {});
    }

    private String required(Map<String, String> arguments, String key) {
        String value = arguments.get(key);
        if (value == null) {
            throw new IllegalArgumentException("缺少参数：" + key);
        }
        return value;
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
        if (path == null || path.isBlank()) {
            path = ".";
        }
        Path relativePath = Path.of(path);
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("必须使用相对路径：" + path);
        }
        Path resolved = workspacePath.resolve(relativePath).normalize();
        if (!resolved.startsWith(workspacePath)) {
            throw new IllegalArgumentException("路径越界：" + path);
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

    private WorkspaceToolResult ok(String content) {
        return WorkspaceToolResult.builder().content(content == null ? "" : content).build();
    }

    private WorkspaceToolResult error(String content) {
        return WorkspaceToolResult.builder()
                .content(content == null || content.isBlank() ? "工具执行失败" : content)
                .error(true)
                .build();
    }
}
