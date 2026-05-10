package org.ymz.app.ai.codegen.workflow;

import org.ymz.app.ai.codegen.workspace.CodeGenerationCommandResult;

import org.ymz.app.ai.codegen.event.CodeGenerationTaskEventRecorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
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
import org.ymz.app.model.dto.app.content.ContentBlock;
import org.ymz.app.model.dto.app.content.TextBlock;
import org.ymz.app.model.dto.app.content.ToolUseBlock;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.ai.monitoring.LlmMonitoringAttributes;
import org.ymz.app.ai.monitoring.LlmMonitoringContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private static final int TOOL_RESULT_LIMIT = 2_000;

    private final CreateCodeGenerationAiService createCodeGenerationAiService;
    private final IterateCodeGenerationAiService iterateCodeGenerationAiService;
    private final RepairCodeGenerationAiService repairCodeGenerationAiService;
    private final ChatCodeGenerationAiService chatCodeGenerationAiService;
    private final CodeGenerationTaskEventRecorder taskEventRecorder;
    private final CodeGenerationInvocationGuard invocationGuard;
    private final CodeGenerationRecoveryContextService recoveryContextService;
    private final ObjectMapper objectMapper;
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
        InvocationParameters parameters = InvocationParameters.from(Map.of(
                "codegenContext", context,
                LlmMonitoringAttributes.CONTEXT, new LlmMonitoringContext(
                        context.getScenario().getMonitoringScenario(),
                        context.getAppId(),
                        context.getTaskId()
                )
        ));
        List<ContentBlock> blockAccumulator = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        AtomicReference<ToolExecutionRequest> pendingToolRequest = new AtomicReference<>();
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
                    currentText.append(delta);
                    taskEventRecorder.publishTextDelta(context, delta);
                })
                .beforeToolExecution(beforeToolExecution -> {
                    // 工具调用前封闭文本块，保证一次回复里的文本和工具顺序可还原。
                    closeTextBlock(blockAccumulator, currentText);
                    pendingToolRequest.set(beforeToolExecution.request());
                    taskEventRecorder.publishToolCalled(context, beforeToolExecution.request());
                })
                .onToolExecuted(toolExecution -> {
                    appendToolUseBlock(blockAccumulator, pendingToolRequest, toolExecution);
                    taskEventRecorder.publishToolExecuted(context, toolExecution);
                })
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

        // 流式响应结束后，只落库一条完整 assistant 消息。
        closeTextBlock(blockAccumulator, currentText);
        WorkspaceToolSession session = parameters.get("workspaceToolSession");
        if (blockAccumulator.isEmpty() && session != null && session.getFinalSummary() != null
                && !session.getFinalSummary().isBlank()) {
            blockAccumulator.add(new TextBlock(session.getFinalSummary().trim()));
        }
        if (!blockAccumulator.isEmpty()) {
            taskEventRecorder.appendAssistantMessage(context, blockAccumulator);
        }
        String summary = summaryFromBlocks(blockAccumulator, session);
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

    private void closeTextBlock(List<ContentBlock> blocks, StringBuilder currentText) {
        String content = currentText.toString().trim();
        if (content.isBlank()) {
            currentText.setLength(0);
            return;
        }
        blocks.add(new TextBlock(content));
        currentText.setLength(0);
    }

    private void appendToolUseBlock(
            List<ContentBlock> blocks,
            AtomicReference<ToolExecutionRequest> pendingToolRequest,
            ToolExecution toolExecution
    ) {
        ToolExecutionRequest request = pendingToolRequest.getAndSet(null);
        if (request == null) {
            request = toolExecution.request();
        }
        blocks.add(new ToolUseBlock(
                request.name(),
                parseToolInput(request.arguments()),
                toolExecution.hasFailed() ? null : truncate(toolResult(toolExecution), TOOL_RESULT_LIMIT)
        ));
    }

    private Object parseToolInput(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, Object.class);
        } catch (JsonProcessingException e) {
            return arguments;
        }
    }

    private String toolResult(ToolExecution toolExecution) {
        try {
            return toolExecution.result();
        } catch (RuntimeException e) {
            Object resultObject = toolExecution.resultObject();
            return resultObject == null ? "" : String.valueOf(resultObject);
        }
    }

    private String truncate(String content, int limit) {
        if (content == null || content.length() <= limit) {
            return content;
        }
        return content.substring(0, limit) + "\n...[truncated]";
    }

    private String summaryFromBlocks(List<ContentBlock> blocks, WorkspaceToolSession session) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (blocks.get(i) instanceof TextBlock textBlock && textBlock.text() != null
                    && !textBlock.text().isBlank()) {
                return textBlock.text();
            }
        }
        return session == null ? "" : session.getFinalSummary();
    }
}
