package org.ymz.app.ai.codegen.workflow;

import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;

import org.ymz.app.ai.codegen.event.CodeGenerationTaskEventRecorder;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.codegen.runtime.CodeGenerationContext;
import org.ymz.app.ai.codegen.runtime.CodeGenerationInvocationGuard;
import org.ymz.app.ai.codegen.memory.CodeGenerationRecoveryContextService;
import org.ymz.app.model.enums.codegen.CodeGenerationScenario;
import org.ymz.app.ai.codegen.tool.WorkspaceToolSession;
import org.ymz.app.ai.codegen.agent.ChatCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.CreateCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.IterateCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.RepairCodeGenerationAiService;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 代码生成工作流门面实现。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class CodeGenerationAgentExecutor {

    private static final long STREAM_TIMEOUT_MINUTES = 30;

    private final CreateCodeGenerationAiService createCodeGenerationAiService;
    private final IterateCodeGenerationAiService iterateCodeGenerationAiService;
    private final RepairCodeGenerationAiService repairCodeGenerationAiService;
    private final ChatCodeGenerationAiService chatCodeGenerationAiService;
    private final CodeGenerationTaskEventRecorder taskEventRecorder;
    private final CodeGenerationInvocationGuard invocationGuard;
    private final CodeGenerationRecoveryContextService recoveryContextService;
    public void generate(App app, AppTask task, Path workspacePath) {
        run(CodeGenerationContext.builder()
                .appId(app.getId())
                .taskId(task.getId())
                .appName(app.getName())
                .workspacePath(workspacePath)
                .scenario(CodeGenerationScenario.CREATE)
                .taskPrompt(task.getPrompt())
                .build());
    }
    public void iterate(App app, AppTask task, Path workspacePath) {
        run(CodeGenerationContext.builder()
                .appId(app.getId())
                .taskId(task.getId())
                .appName(app.getName())
                .workspacePath(workspacePath)
                .scenario(CodeGenerationScenario.ITERATE)
                .taskPrompt(task.getPrompt())
                .build());
    }
    public void repair(App app, AppTask task, Path workspacePath, CodeGenerationCommandResult failedCommand, int repairAttempt) {
        run(CodeGenerationContext.builder()
                .appId(app.getId())
                .taskId(task.getId())
                .appName(app.getName())
                .workspacePath(workspacePath)
                .scenario(CodeGenerationScenario.REPAIR)
                .taskPrompt(task.getPrompt())
                .failedCommand(failedCommand)
                .repairAttempt(repairAttempt)
                .build());
    }
    public void chat(App app, AppTask task, Path workspacePath) {
        run(CodeGenerationContext.builder()
                .appId(app.getId())
                .taskId(task.getId())
                .appName(app.getName())
                .workspacePath(workspacePath)
                .scenario(CodeGenerationScenario.CHAT)
                .taskPrompt(task.getPrompt())
                .build());
    }

    private void run(CodeGenerationContext context) {
        invocationGuard.withAppLock(context.getAppId(), () -> {
            execute(context);
            return null;
        });
    }

    private void execute(CodeGenerationContext context) {
        taskEventRecorder.publishRunStarted(context);
        // 当 Redis 中的短期记忆已过期时，用长期摘要和最近关键消息重新热启动上下文。
        recoveryContextService.bootstrapIfNeeded(context);
        InvocationParameters parameters = InvocationParameters.from(Map.of("codegenContext", context));
        StringBuilder assistantBuffer = new StringBuilder();
        AtomicReference<String> lastFlushedAssistantMessage = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<ChatResponse> responseRef = new AtomicReference<>();

        TokenStream tokenStream = switch (context.getScenario()) {
            case CREATE -> createCodeGenerationAiService.generate(context.memoryId(), context.getTaskPrompt(), parameters);
            case ITERATE -> iterateCodeGenerationAiService.iterate(context.memoryId(), context.getTaskPrompt(), parameters);
            case REPAIR -> repairCodeGenerationAiService.repair(context.memoryId(), context.getTaskPrompt(), parameters);
            case CHAT -> chatCodeGenerationAiService.chat(context.memoryId(), context.getTaskPrompt(), parameters);
        };

        tokenStream
                .onPartialResponse(delta -> {
                    assistantBuffer.append(delta);
                    taskEventRecorder.publishTextDelta(context, delta);
                })
                .beforeToolExecution(beforeToolExecution -> {
                    // assistant 普通文本只按“文本轮次”落库，进入工具调用前先切一条完整消息。
                    flushAssistantBuffer(context, assistantBuffer, lastFlushedAssistantMessage);
                    taskEventRecorder.publishToolCalled(context, beforeToolExecution.request());
                })
                .onToolExecuted(toolExecution ->
                        taskEventRecorder.publishToolExecuted(context, toolExecution))
                .onCompleteResponse(response -> {
                    responseRef.set(response);
                    latch.countDown();
                })
                .onError(error -> {
                    errorRef.set(error);
                    latch.countDown();
                })
                .start();

        awaitCompletion(latch);
        Throwable error = errorRef.get();
        if (error != null) {
            taskEventRecorder.publishRunFailed(context, error.getMessage());
            throw new IllegalStateException("代码生成失败：" + error.getMessage(), error);
        }

        // 流式响应结束后，再把最后一段未触发工具调用的 assistant 文本落库。
        String assistantMessage = flushAssistantBuffer(context, assistantBuffer, lastFlushedAssistantMessage);
        WorkspaceToolSession session = parameters.get("workspaceToolSession");
        if ((assistantMessage == null || assistantMessage.isBlank()) && session != null && session.getFinalSummary() != null) {
            assistantMessage = session.getFinalSummary().trim();
        }
        if (assistantMessage != null && !assistantMessage.isBlank() && shouldAppendFinalSummary(
                assistantMessage,
                lastFlushedAssistantMessage.get()
        )) {
            // 没有可分段文本时，用 finish(summary) 兜底补一条最终助手消息。
            taskEventRecorder.appendAssistantMessage(context, assistantMessage);
            lastFlushedAssistantMessage.set(assistantMessage);
        }

        String summary = lastFlushedAssistantMessage.get();
        if ((summary == null || summary.isBlank()) && session != null) {
            summary = session.getFinalSummary();
        }
        taskEventRecorder.publishRunFinished(context, summary == null ? "" : summary);
        responseRef.get();
    }

    private void awaitCompletion(CountDownLatch latch) {
        try {
            boolean completed = latch.await(STREAM_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                throw new IllegalStateException("代码生成模型响应超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("代码生成被中断", e);
        }
    }

    String flushAssistantBuffer(
            CodeGenerationContext context,
            StringBuilder assistantBuffer,
            AtomicReference<String> lastFlushedAssistantMessage
    ) {
        String content = assistantBuffer.toString().trim();
        if (content.isBlank()) {
            assistantBuffer.setLength(0);
            return "";
        }
        // 只保存“可独立阅读的完整段落”，不保存 token 级碎片。
        taskEventRecorder.appendAssistantMessage(context, content);
        assistantBuffer.setLength(0);
        lastFlushedAssistantMessage.set(content);
        return content;
    }

    boolean shouldAppendFinalSummary(String assistantMessage, String lastFlushedAssistantMessage) {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            return false;
        }
        if (lastFlushedAssistantMessage == null || lastFlushedAssistantMessage.isBlank()) {
            return true;
        }
        return !assistantMessage.trim().equals(lastFlushedAssistantMessage.trim());
    }
}
