package org.ymz.app.ai.codegen.memory;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.codegen.runtime.CodeGenerationContext;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.service.AppChatMessageService;

import java.util.ArrayList;
import java.util.List;

import static org.ymz.app.model.entity.table.AppChatMessageTableDef.APP_CHAT_MESSAGE;
import static org.ymz.app.model.enums.app.AppChatMessageRole.ASSISTANT;
import static org.ymz.app.model.enums.app.AppChatMessageRole.SYSTEM;
import static org.ymz.app.model.enums.app.AppChatMessageRole.USER;

/**
 * 从长期摘要和最近关键消息恢复短期工作记忆。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class CodeGenerationRecoveryContextService {

    private static final int MAX_RECENT_MESSAGES = 6;

    private final CodeGenerationChatMemoryFactory memoryFactory;
    private final AppContextSummaryManager appContextSummaryManager;
    private final AppChatMessageService appChatMessageService;

    public void bootstrapIfNeeded(CodeGenerationContext context) {
        ChatMemory memory = memoryFactory.create(context.getAppId());
        List<ChatMessage> existingMessages = memory.messages();
        if (existingMessages != null && !existingMessages.isEmpty()) {
            return;
        }

        String bootstrapContent = bootstrapContent(context);
        if (StrUtil.isBlank(bootstrapContent)) {
            return;
        }

        // 用“一条用户提示 + 一条 assistant 摘要”的形式灌入工作记忆，避免把长期历史逐条回放进模型窗口。
        memory.add(UserMessage.from("请记住以下应用上下文，这些内容来自长期摘要与最近关键消息。"));
        memory.add(AiMessage.from(bootstrapContent));
    }

    public String bootstrapContent(CodeGenerationContext context) {
        AppContextSummaryPayload summary = appContextSummaryManager.loadPayload(context.getAppId());
        List<AppChatMessage> recentMessages = recentMessages(context);
        if (summary == null && recentMessages.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        if (summary != null) {
            builder.append("## 当前应用摘要\n");
            builder.append("应用名称：").append(StrUtil.blankToDefault(summary.getAppName(), "")).append('\n');
            appendList(builder, "页面", summary.getPages());
            appendList(builder, "路由", summary.getRoutes());
            appendList(builder, "核心功能", summary.getCoreFeatures());
            appendList(builder, "状态模型", summary.getStateModels());
            appendList(builder, "视觉风格", summary.getVisualStyles());
            appendList(builder, "约束", summary.getConstraints());
            appendList(builder, "已知问题", summary.getKnownIssues());
        }
        if (!recentMessages.isEmpty()) {
            builder.append("## 最近关键消息\n");
            for (AppChatMessage message : recentMessages) {
                builder.append(message.getRole())
                        .append(": ")
                        .append(StrUtil.blankToDefault(message.getContent(), ""))
                        .append('\n');
            }
        }
        return builder.toString().trim();
    }

    private List<AppChatMessage> recentMessages(CodeGenerationContext context) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_CHAT_MESSAGE.ALL_COLUMNS)
                .from(APP_CHAT_MESSAGE)
                .where(APP_CHAT_MESSAGE.APP_ID.eq(context.getAppId()))
                .and(APP_CHAT_MESSAGE.ROLE.in(List.of(USER.name(), ASSISTANT.name(), SYSTEM.name())))
                .and(APP_CHAT_MESSAGE.TASK_ID.ne(context.getTaskId()))
                .orderBy(APP_CHAT_MESSAGE.ID.desc())
                .limit(MAX_RECENT_MESSAGES);
        List<AppChatMessage> messages = appChatMessageService.list(query);
        // 查询时按倒序拿最近消息，返回前再恢复成自然阅读顺序。
        List<AppChatMessage> ordered = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            ordered.add(messages.get(i));
        }
        return ordered;
    }

    private void appendList(StringBuilder builder, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(title).append("：\n");
        for (String value : values) {
            builder.append("- ").append(value).append('\n');
        }
    }
}
