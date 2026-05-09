package org.ymz.app.ai.codegen.memory;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.model.dto.app.content.ContentBlock;
import org.ymz.app.model.dto.app.content.TextBlock;
import org.ymz.app.model.dto.app.content.ToolUseBlock;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageContentType;
import org.ymz.app.service.AppChatMessageService;
import org.ymz.app.service.AppService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.ymz.app.model.entity.table.AppChatMessageTableDef.APP_CHAT_MESSAGE;
import static org.ymz.app.model.enums.app.AppChatMessageRole.ASSISTANT;
import static org.ymz.app.model.enums.app.AppChatMessageRole.USER;

/**
 * 管理应用长期上下文摘要。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AppContextSummaryManager {

    private static final int MAX_RECENT_MESSAGES = 6;
    private static final int MAX_LIST_ITEMS = 200;

    private final AppContextSummaryAssistant assistant;
    private final AppService appService;
    private final AppChatMessageService appChatMessageService;
    private final ObjectMapper objectMapper;

    public void refresh(App app, AppTask task, Path workspacePath) {
        App existing = appService.getById(app.getId());
        AppContextSummaryPayload payload;
        try {
            // 优先让模型基于“已有摘要 + 最近关键消息 + 当前文件树”生成新的结构化摘要。
            payload = assistant.summarize(summaryPrompt(app, task, workspacePath, existing));
            normalize(payload, app);
        } catch (Exception e) {
            // 摘要生成失败时也要保留一个可恢复的最小摘要，避免下一轮完全失忆。
            payload = fallbackSummary(app, task, existing);
        }
        String summaryJson = toJson(payload);
        LocalDateTime now = LocalDateTime.now();
        appService.updateById(App.builder()
                .id(app.getId())
                .contextSummaryJson(summaryJson)
                .contextSummaryTaskId(task.getId())
                .contextSummaryUpdatedAt(now)
                .build());
    }

    public AppContextSummaryPayload loadPayload(Long appId) {
        App app = appService.getById(appId);
        if (app == null || StrUtil.isBlank(app.getContextSummaryJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(app.getContextSummaryJson(), AppContextSummaryPayload.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String summaryPrompt(App app, AppTask task, Path workspacePath, App existing) {
        StringBuilder builder = new StringBuilder();
        builder.append("应用名称：").append(app.getName()).append('\n');
        builder.append("应用初始需求：\n").append(StrUtil.blankToDefault(app.getInitPrompt(), "")).append('\n');
        builder.append("当前任务类型：").append(task.getTaskType()).append('\n');
        builder.append("当前任务输入：\n").append(StrUtil.blankToDefault(task.getPrompt(), "")).append('\n');
        if (existing != null && StrUtil.isNotBlank(existing.getContextSummaryJson())) {
            builder.append("已有结构化摘要：\n").append(existing.getContextSummaryJson()).append('\n');
        }
        builder.append("最近关键消息：\n").append(recentMessages(app.getId(), task.getId())).append('\n');
        builder.append("当前项目文件：\n").append(currentFiles(workspacePath)).append('\n');
        return builder.toString();
    }

    private String recentMessages(Long appId, Long excludeTaskId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_CHAT_MESSAGE.ALL_COLUMNS)
                .from(APP_CHAT_MESSAGE)
                .where(APP_CHAT_MESSAGE.APP_ID.eq(appId))
                .and(APP_CHAT_MESSAGE.ROLE.in(List.of(USER.name(), ASSISTANT.name())))
                .and(APP_CHAT_MESSAGE.TASK_ID.ne(excludeTaskId))
                .orderBy(APP_CHAT_MESSAGE.ID.desc())
                .limit(MAX_RECENT_MESSAGES);
        List<AppChatMessage> messages = appChatMessageService.list(query);
        List<String> lines = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            AppChatMessage message = messages.get(i);
            lines.add(message.getRole() + ": " + readableContent(message));
        }
        return lines.isEmpty() ? "无" : String.join("\n", lines);
    }

    private String readableContent(AppChatMessage message) {
        if (!AppChatMessageContentType.BLOCKS.name().equals(message.getContentType())) {
            return StrUtil.blankToDefault(message.getContent(), "");
        }
        try {
            List<ContentBlock> blocks = objectMapper.readValue(
                    message.getContent(),
                    new TypeReference<List<ContentBlock>>() {
                    }
            );
            List<String> lines = new ArrayList<>();
            for (ContentBlock block : blocks) {
                if (block instanceof TextBlock textBlock && StrUtil.isNotBlank(textBlock.text())) {
                    lines.add(textBlock.text());
                } else if (block instanceof ToolUseBlock toolUseBlock) {
                    lines.add("工具调用: " + toolUseBlock.name()
                            + (StrUtil.isBlank(toolUseBlock.result()) ? "" : "\n结果: " + toolUseBlock.result()));
                }
            }
            return lines.isEmpty() ? "" : String.join("\n", lines);
        } catch (JsonProcessingException e) {
            return StrUtil.blankToDefault(message.getContent(), "");
        }
    }

    private String currentFiles(Path workspacePath) {
        if (workspacePath == null || !Files.isDirectory(workspacePath)) {
            return "无";
        }
        try (var stream = Files.walk(workspacePath, 5)) {
            return stream
                    .filter(path -> !path.equals(workspacePath))
                    .filter(path -> !isIgnored(workspacePath, path))
                    .limit(MAX_LIST_ITEMS)
                    .map(path -> {
                        String relative = workspacePath.relativize(path).toString().replace('\\', '/');
                        return Files.isDirectory(path) ? relative + "/" : relative;
                    })
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("无");
        } catch (IOException e) {
            return "无";
        }
    }

    private boolean isIgnored(Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        return relative.equals("node_modules")
                || relative.startsWith("node_modules/")
                || relative.equals("dist")
                || relative.startsWith("dist/")
                || relative.equals(".git")
                || relative.startsWith(".git/");
    }

    private AppContextSummaryPayload fallbackSummary(App app, AppTask task, App existing) {
        AppContextSummaryPayload payload = loadPayload(app.getId());
        if (payload != null) {
            return payload;
        }
        List<String> features = new ArrayList<>();
        if (StrUtil.isNotBlank(app.getInitPrompt())) {
            features.add("初始需求: " + app.getInitPrompt());
        }
        if (StrUtil.isNotBlank(task.getPrompt())) {
            features.add("最近任务: " + task.getPrompt());
        }
        return AppContextSummaryPayload.builder()
                .appName(app.getName())
                .coreFeatures(features)
                .constraints(existing == null ? List.of("摘要生成失败，使用兜底摘要") : List.of())
                .knownIssues(List.of("摘要生成失败，待下次成功任务刷新"))
                .build();
    }

    private void normalize(AppContextSummaryPayload payload, App app) {
        if (payload == null) {
            throw new IllegalStateException("摘要生成结果为空");
        }
        // 统一把列表字段补成非 null，减少后续恢复链路里的判空分支。
        if (StrUtil.isBlank(payload.getAppName())) {
            payload.setAppName(app.getName());
        }
        if (payload.getPages() == null) {
            payload.setPages(new ArrayList<>());
        }
        if (payload.getRoutes() == null) {
            payload.setRoutes(new ArrayList<>());
        }
        if (payload.getCoreFeatures() == null) {
            payload.setCoreFeatures(new ArrayList<>());
        }
        if (payload.getStateModels() == null) {
            payload.setStateModels(new ArrayList<>());
        }
        if (payload.getVisualStyles() == null) {
            payload.setVisualStyles(new ArrayList<>());
        }
        if (payload.getConstraints() == null) {
            payload.setConstraints(new ArrayList<>());
        }
        if (payload.getKnownIssues() == null) {
            payload.setKnownIssues(new ArrayList<>());
        }
    }

    private String toJson(AppContextSummaryPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化应用摘要", e);
        }
    }
}
