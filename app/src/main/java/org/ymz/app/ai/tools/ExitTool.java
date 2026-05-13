package org.ymz.app.ai.tools;

import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ymz.app.config.AppPathProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 为 AI 提供一个专门的退出工具，让它能够主动结束工具调用循环，从而避免 AI 陷入死循环
 * 并且系统将在此对于 AI 修改后的项目进行验收：ESLint 校验以及预览构建
 * 
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExitTool implements BaseTool {
    private final AppPathProperties appPathProperties;

    @Override
    public String toolName() {
        return "exit";
    }

    @Override
    public String displayName() {
        return "退出工具调用";
    }

    @Override
    public String formatRequest(JSONObject arguments) {
        return "\n\n【选择工具】提交系统验收\n";
    }

    @Override
    public String formatResponse(JSONObject arguments, String result) {
        return "\n【工具调用结果】系统验收通过\n\n";
    }

    @Tool("任务完成时调用，提交系统验收；通过后结束，失败则修复后重试")
    public String exit(@ToolMemoryId Long appId) {
        log.debug("AI 调用退出工具， 请求参数：appId={}，系统验收中...", appId);
        Path workspacePath = appPathProperties.getTmpDir()
                .resolve("app-workspace")
                .resolve(String.valueOf(appId))
                .normalize();

        String pnpm = System.getProperty("os.name").toLowerCase().contains("win") ? "pnpm.cmd" : "pnpm";
        String[][] commands = {
                { "lint", "ESLint 校验" },
                { "build:preview", "预览构建" }
        };
        for (String[] command : commands) {
            String commandName = command[0];
            String displayName = command[1];
            log.debug("应用 {} 执行{}中...", appId, displayName);
            try {
                Process process = new ProcessBuilder(pnpm, commandName)
                        .directory(workspacePath.toFile())
                        .redirectErrorStream(true)
                        .start();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                Thread outputReader = Thread.startVirtualThread(() -> {
                    try {
                        process.getInputStream().transferTo(outputStream);
                    } catch (IOException e) {
                        log.warn("读取{}输出失败: appId={}", displayName, appId, e);
                    }
                });

                boolean finished = process.waitFor(10, TimeUnit.MINUTES);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("应用 {} 执行{}超时", appId, displayName);
                    return "系统内部错误，请稍后重试";
                }

                outputReader.join();
                String commandOutput = outputStream.toString(StandardCharsets.UTF_8);
                if (process.exitValue() != 0) {
                    log.warn("应用 {} 执行{}失败，exitCode={}", appId, displayName, process.exitValue());
                    return """
                            系统验收失败：%s未通过，请根据以下错误修复后再次调用退出工具：

                            %s
                            """.formatted(displayName, commandOutput);
                }
            } catch (IOException e) {
                log.warn("应用 {} 执行{}时发生系统错误", appId, displayName, e);
                return "系统内部错误，请稍后重试";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("应用 {} 执行{}被中断", appId, displayName, e);
                return "系统内部错误，请稍后重试";
            }
        }

        try {
            Path distPath = workspacePath.resolve("dist").normalize();
            Path previewPath = appPathProperties.getTmpDir()
                    .resolve("app-previews")
                    .resolve(String.valueOf(appId))
                    .normalize();

            if (Files.exists(previewPath)) {
                try (Stream<Path> stream = Files.walk(previewPath)) {
                    // 覆盖发布前先清空旧预览，避免残留文件影响用户看到的结果。
                    for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(item);
                    }
                }
            }
            try (Stream<Path> stream = Files.walk(distPath)) {
                for (Path sourceItem : stream.toList()) {
                    // 保留 dist 内部结构，发布到当前应用的预览目录。
                    Path targetItem = previewPath.resolve(distPath.relativize(sourceItem)).normalize();
                    if (Files.isDirectory(sourceItem)) {
                        Files.createDirectories(targetItem);
                    } else {
                        Files.createDirectories(targetItem.getParent());
                        Files.copy(sourceItem, targetItem, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            log.debug("AI 调用退出工具成功");
            return "系统验收通过，请停止调用工具并输出最终结果";
        } catch (IOException e) {
            log.warn("应用 {} 发布预览构建产物失败", appId, e);
            return "系统内部错误，请稍后重试";
        }
    }

//    @Override
//    public String formatResponse(JSONObject arguments) {
//        return "\n\n[执行结束]\n\n";
//    }
}
