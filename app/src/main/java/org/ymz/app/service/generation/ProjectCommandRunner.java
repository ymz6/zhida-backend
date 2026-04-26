package org.ymz.app.service.generation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 执行由后端固定控制的项目命令。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class ProjectCommandRunner {

    private static final long COMMAND_TIMEOUT_SECONDS = 600;

    private final AppTaskLogPublisher appTaskLogPublisher;

    public enum LogMode {

        FULL,
        SUMMARY,
        TRANSIENT_ONLY
    }

    public void runPnpmCommand(Long appId, Long taskId, Path workspacePath, String... args) {
        runPnpmCommand(appId, taskId, workspacePath, LogMode.FULL, args);
    }

    public void runPnpmCommand(Long appId, Long taskId, Path workspacePath, LogMode logMode, String... args) {
        ProjectCommandResult result = runPnpmCommandResult(appId, taskId, workspacePath, logMode, args);
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
    }

    public ProjectCommandResult runPnpmCommandResult(Long appId, Long taskId, Path workspacePath, String... args) {
        return runPnpmCommandResult(appId, taskId, workspacePath, LogMode.FULL, args);
    }

    public ProjectCommandResult runPnpmCommandResult(
            Long appId,
            Long taskId,
            Path workspacePath,
            LogMode logMode,
            String... args
    ) {
        String pnpm = isWindows() ? "pnpm.cmd" : "pnpm";
        String[] command = new String[args.length + 1];
        command[0] = pnpm;
        System.arraycopy(args, 0, command, 1, args.length);
        return runCommandResult(appId, taskId, workspacePath, command, logMode);
    }

    void runCommand(Long appId, Long taskId, Path workspacePath, String[] command) {
        runCommand(appId, taskId, workspacePath, command, LogMode.FULL);
    }

    void runCommand(Long appId, Long taskId, Path workspacePath, String[] command, LogMode logMode) {
        ProjectCommandResult result = runCommandResult(appId, taskId, workspacePath, command, logMode);
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
    }

    ProjectCommandResult runCommandResult(Long appId, Long taskId, Path workspacePath, String[] command) {
        return runCommandResult(appId, taskId, workspacePath, command, LogMode.FULL);
    }

    ProjectCommandResult runCommandResult(
            Long appId,
            Long taskId,
            Path workspacePath,
            String[] command,
            LogMode logMode
    ) {
        String commandText = String.join(" ", command);
        StringBuffer logContent = new StringBuffer("$ " + commandText);
        long startedAt = System.currentTimeMillis();
        int exitCode = -1;
        boolean success = false;
        boolean timedOut = false;
        String errorMessage = null;

        appTaskLogPublisher.publishTransientMessage(
                appId,
                taskId,
                AppChatMessageRole.TOOL,
                AppChatMessageType.BUILD_LOG,
                "$ " + commandText,
                Map.of("command", Arrays.asList(command))
        );

        Process process = null;
        Thread logThread = null;
        AtomicReference<IOException> logErrorRef = new AtomicReference<>();
        try {
            process = new ProcessBuilder(command)
                    .directory(workspacePath.toFile())
                    .redirectErrorStream(true)
                    .start();

            Process runningProcess = process;
            logThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logContent.append("\n").append(line);
                        appTaskLogPublisher.publishTransientMessage(
                                appId,
                                taskId,
                                AppChatMessageRole.TOOL,
                                AppChatMessageType.BUILD_LOG,
                                line,
                                Map.of("command", commandText)
                        );
                    }
                } catch (IOException e) {
                    logErrorRef.set(e);
                }
            }, "app-command-log-" + taskId);
            logThread.start();

            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                timedOut = true;
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            if (timedOut) {
                logThread.join(TimeUnit.SECONDS.toMillis(5));
            } else {
                logThread.join();
            }
            if (logErrorRef.get() != null) {
                errorMessage = "读取命令输出失败：" + commandText;
            } else if (timedOut) {
                errorMessage = "命令执行超时：" + commandText;
            } else {
                exitCode = process.exitValue();
                if (exitCode == 0) {
                    success = true;
                } else {
                    errorMessage = "命令执行失败：" + commandText + "，退出码：" + exitCode;
                }
            }
        } catch (IOException e) {
            errorMessage = "无法执行命令：" + commandText;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorMessage = "命令执行被中断：" + commandText;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (logThread != null && logThread.isAlive()) {
                logThread.interrupt();
            }
        }

        long durationMillis = System.currentTimeMillis() - startedAt;
        ProjectCommandResult result = ProjectCommandResult.builder()
                .command(command)
                .commandText(commandText)
                .content(logContent.toString())
                .exitCode(exitCode >= 0 ? exitCode : null)
                .success(success)
                .timedOut(timedOut)
                .durationMillis(durationMillis)
                .errorMessage(errorMessage)
                .build();
        persistResult(appId, taskId, result, logMode);
        return result;
    }

    private void persistResult(Long appId, Long taskId, ProjectCommandResult result, LogMode logMode) {
        if (LogMode.TRANSIENT_ONLY.equals(logMode)) {
            return;
        }
        String content = LogMode.SUMMARY.equals(logMode) ? summary(result) : result.getContent();
        appTaskLogPublisher.appendMessage(
                appId,
                taskId,
                AppChatMessageRole.TOOL,
                AppChatMessageType.BUILD_LOG,
                content,
                metadata(result)
        );
    }

    private String summary(ProjectCommandResult result) {
        String status = result.isSuccess() ? "命令执行成功" : "命令执行失败";
        StringBuilder builder = new StringBuilder("$ ")
                .append(result.getCommandText())
                .append("\n")
                .append(status)
                .append("，耗时 ")
                .append(result.getDurationMillis())
                .append("ms");
        if (result.getExitCode() != null) {
            builder.append("，退出码 ").append(result.getExitCode());
        }
        if (!result.isSuccess() && result.getErrorMessage() != null) {
            builder.append("\n").append(result.getErrorMessage());
            builder.append("\n").append(tail(result.getContent(), 2000));
        }
        return builder.toString();
    }

    private String tail(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content == null ? "" : content;
        }
        return "...[truncated]\n" + content.substring(content.length() - maxChars);
    }

    private Map<String, Object> metadata(ProjectCommandResult result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("command", Arrays.asList(result.getCommand()));
        metadata.put("exitCode", result.getExitCode());
        metadata.put("success", result.isSuccess());
        metadata.put("durationMillis", result.getDurationMillis());
        metadata.put("timedOut", result.isTimedOut());
        if (result.getErrorMessage() != null) {
            metadata.put("errorMessage", result.getErrorMessage());
        }
        return metadata;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
