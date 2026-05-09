package org.ymz.app.ai.codegen.tool;

import lombok.Getter;
import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;
import org.ymz.app.ai.codegen.workspace.CodeGenerationProjectVerifier;
import org.ymz.app.ai.codegen.workspace.CodeGenerationWorkspaceRules;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * 单次工具调用会话。
 *
 * @author ymz
 */
public class WorkspaceToolSession {

    @Getter
    private final Path workspacePath;
    private final CodeGenerationProjectVerifier projectVerifier;
    private final Long appId;
    private final Long taskId;
    private final Set<String> readPaths = new HashSet<>();

    private boolean lintPassed;
    private boolean buildPassed;
    @Getter
    private String finalSummary;

    public WorkspaceToolSession(
            Path workspacePath,
            CodeGenerationProjectVerifier projectVerifier,
            Long appId,
            Long taskId
    ) {
        if (workspacePath == null) {
            throw new IllegalArgumentException("工作区路径不能为空");
        }
        this.workspacePath = workspacePath.toAbsolutePath().normalize();
        this.projectVerifier = projectVerifier;
        this.appId = appId;
        this.taskId = taskId;
    }

    public Path resolveReadPath(String path) {
        Path resolved = resolvePath(path);
        if (isIgnored(resolved)) {
            throw new IllegalArgumentException("不允许访问该路径：" + path);
        }
        return resolved;
    }

    public Path resolveWritePath(String path) {
        Path resolved = resolvePath(path);
        if (isIgnored(resolved) || isProtected(resolved)) {
            throw new IllegalArgumentException("不允许修改该路径：" + path);
        }
        return resolved;
    }

    public String toRelative(Path path) {
        return CodeGenerationWorkspaceRules.toRelative(workspacePath, path);
    }

    public void markRead(Path path) {
        readPaths.add(toRelative(path));
    }

    public void requireRead(Path path) {
        String relativePath = toRelative(path);
        if (!readPaths.contains(relativePath)) {
            throw new IllegalArgumentException("请先调用 readFile 阅读 " + relativePath + " 后再修改");
        }
    }

    public void markWritten(Path path) {
        String relativePath = toRelative(path);
        readPaths.remove(relativePath);
        // 工作区一旦变更，之前通过的校验和最终总结都不再可信。
        lintPassed = false;
        buildPassed = false;
        finalSummary = null;
    }

    public void markLintPassed() {
        lintPassed = true;
        buildPassed = false;
    }

    public void markLintFailed() {
        lintPassed = false;
        buildPassed = false;
    }

    public void requireLintPassed() {
        if (!lintPassed) {
            throw new IllegalStateException("请先调用 check 并确认 pnpm lint 通过后再调用 build");
        }
    }

    public void markBuildPassed() {
        buildPassed = true;
    }

    public void markBuildFailed() {
        buildPassed = false;
    }

    public void requireFinishReady() {
        if (!lintPassed || !buildPassed) {
            throw new IllegalStateException("请确认 check 和 build 都已通过且代码未再变更后再调用 finish");
        }
    }

    public String setFinalSummary(String summary) {
        finalSummary = summary;
        return summary;
    }

    public CodeGenerationCommandResult runLint() {
        requireProjectVerifier("check");
        return projectVerifier.runLint(appId, taskId, workspacePath);
    }

    public CodeGenerationCommandResult runBuild() {
        requireProjectVerifier("build");
        return projectVerifier.runBuild(appId, taskId, workspacePath);
    }

    boolean isIgnored(Path path) {
        return CodeGenerationWorkspaceRules.isIgnored(workspacePath, path);
    }

    private void requireProjectVerifier(String toolName) {
        if (projectVerifier == null || appId == null || taskId == null) {
            throw new IllegalStateException("当前环境未配置项目校验工具，无法运行 " + toolName);
        }
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

    private boolean isProtected(Path path) {
        return CodeGenerationWorkspaceRules.isProtectedRelativePath(toRelative(path));
    }
}
