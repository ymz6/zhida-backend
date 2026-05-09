package org.ymz.app.ai.codegen.workspace;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 代码生成工作区的共享规则。
 *
 * @author ymz
 */
public final class CodeGenerationWorkspaceRules {

    public static final int MAX_FILE_READ_BYTES = 200_000;
    public static final int MAX_FILE_WRITE_CHARS = 600_000;
    public static final int MAX_TOOL_LIST_ITEMS = 300;
    public static final int MAX_SUMMARY_FILE_LIST_ITEMS = 200;
    public static final int MAX_SEARCH_MATCHES = 60;
    public static final int MAX_CHECK_LOG_CHARS = 12_000;
    public static final long SEARCH_TIMEOUT_SECONDS = 10;

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            "node_modules",
            "dist",
            ".git"
    );

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

    private static final List<String> RIPGREP_IGNORED_GLOBS = List.of(
            "!node_modules/**",
            "!dist/**",
            "!.git/**"
    );

    private CodeGenerationWorkspaceRules() {
    }

    public static String toRelative(Path root, Path path) {
        return root.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    public static boolean isIgnored(Path root, Path path) {
        return isIgnoredRelativePath(toRelative(root, path));
    }

    public static boolean isIgnoredRelativePath(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        for (String ignoredDirectory : IGNORED_DIRECTORIES) {
            if (normalized.equals(ignoredDirectory) || normalized.startsWith(ignoredDirectory + "/")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isProtectedRelativePath(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        return PROTECTED_EXACT_PATHS.contains(normalized)
                || normalized.equals("src/components/ui")
                || normalized.startsWith("src/components/ui/");
    }

    public static List<String> ripgrepIgnoredGlobs() {
        return RIPGREP_IGNORED_GLOBS;
    }

    private static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        String normalized = relativePath.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }
}
