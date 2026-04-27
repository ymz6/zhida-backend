package org.ymz.app.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;
import org.ymz.app.service.generation.AppTaskLogPublisher;
import org.ymz.app.service.generation.ProjectCommandResult;
import org.ymz.app.service.generation.ProjectCommandRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 运行受限工具调用循环，生成 React 应用代码。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class CodeGenerationAgent {

    private static final int MAX_AGENT_TURNS = 25;
    private static final int MODEL_TIMEOUT_SECONDS = 240;
    private static final int LOG_PREVIEW_LIMIT = 3000;

    private final StreamingChatModel codeGenerateModel;
    private final ObjectMapper objectMapper;
    private final AppTaskLogPublisher appTaskLogPublisher;
    private final ProjectCommandRunner projectCommandRunner;
    private final CodeGenerationChatMemoryFactory chatMemoryFactory;

    public void generate(App app, AppTask task, Path workspacePath) {
        WorkspaceToolExecutor tools = new WorkspaceToolExecutor(
                workspacePath,
                objectMapper,
                projectCommandRunner,
                app.getId(),
                task.getId()
        );
        runAgentSession(app, task, tools, initialUserMessage(app, task, tools), true);
    }

    public void iterate(App app, AppTask task, Path workspacePath) {
        WorkspaceToolExecutor tools = new WorkspaceToolExecutor(
                workspacePath,
                objectMapper,
                projectCommandRunner,
                app.getId(),
                task.getId()
        );
        runAgentSession(app, task, tools, iterationUserMessage(app, task, tools), true);
    }

    public void repair(App app, AppTask task, Path workspacePath, ProjectCommandResult failedCommand, int repairAttempt) {
        WorkspaceToolExecutor tools = new WorkspaceToolExecutor(
                workspacePath,
                objectMapper,
                projectCommandRunner,
                app.getId(),
                task.getId()
        );
        runAgentSession(app, task, tools, repairUserMessage(app, task, tools, failedCommand, repairAttempt), false);
    }

    private void runAgentSession(
            App app,
            AppTask task,
            WorkspaceToolExecutor tools,
            String userMessage,
            boolean rememberResult
    ) {
        ChatMemory chatMemory = chatMemoryFactory.create(app.getId());
        List<ChatMessage> messages = sessionMessages(chatMemory, userMessage);

        boolean finished = false;
        String finalSummary = null;
        for (int turn = 0; turn < MAX_AGENT_TURNS && !finished; turn++) {
            ChatResponse response = chat(messages, tools);
            AiMessage aiMessage = response.aiMessage();
            if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
                publishAssistantMessage(app, task, aiMessage.text());
            }

            messages.add(aiMessage);
            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
            if (toolRequests == null || toolRequests.isEmpty()) {
                finished = true;
                break;
            }

            for (ToolExecutionRequest toolRequest : toolRequests) {
                publishToolCall(app, task, toolRequest);

                WorkspaceToolResult toolResult = tools.execute(toolRequest);
                publishToolResult(app, task, toolRequest, toolResult);

                messages.add(ToolExecutionResultMessage.builder()
                        .id(toolRequest.id())
                        .toolName(toolRequest.name())
                        .text(toolResult.getContent())
                        .isError(toolResult.isError())
                        .build());

                if (toolResult.isFinished()) {
                    finished = true;
                    finalSummary = toolResult.getContent();
                }
            }
        }

        if (!finished) {
            throw new IllegalStateException("Agent 未在限制轮次内完成生成");
        }
        if (rememberResult) {
            rememberSuccessfulGeneration(chatMemory, app, task, finalSummary);
        }
    }

    private List<ChatMessage> sessionMessages(ChatMemory chatMemory, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(loadSystemPrompt()));
        for (ChatMessage message : chatMemory.messages()) {
            if (!(message instanceof SystemMessage)) {
                messages.add(message);
            }
        }
        messages.add(UserMessage.from(userMessage));
        return messages;
    }

    private void rememberSuccessfulGeneration(ChatMemory chatMemory, App app, AppTask task, String finalSummary) {
        if (finalSummary == null || finalSummary.isBlank()) {
            return;
        }
        chatMemory.add(UserMessage.from(compactUserMessage(app, task)));
        chatMemory.add(AiMessage.from(finalSummary));
    }

    private ChatResponse chat(List<ChatMessage> messages, WorkspaceToolExecutor tools) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(tools.toolSpecifications())
                .build();

        codeGenerateModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                responseRef.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(MODEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException("代码生成模型响应超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("代码生成被中断", e);
        }

        Throwable error = errorRef.get();
        if (error != null) {
            throw new IllegalStateException("代码生成模型调用失败：" + error.getMessage(), error);
        }
        ChatResponse response = responseRef.get();
        if (response == null) {
            throw new IllegalStateException("代码生成模型未返回结果");
        }
        return response;
    }

    String loadSystemPrompt() {
        ClassPathResource resource = new ClassPathResource("prompt/code-gen-agent.md");
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取代码生成提示词", e);
        }
    }

    private void publishAssistantMessage(App app, AppTask task, String content) {
        appTaskLogPublisher.appendMessage(
                app.getId(),
                task.getId(),
                AppChatMessageRole.ASSISTANT,
                AppChatMessageType.CHAT,
                content
        );
    }

    private void publishToolCall(App app, AppTask task, ToolExecutionRequest toolRequest) {
        String content = summarizeToolCall(toolRequest);
        Map<String, Object> metadata = Map.of("toolName", toolRequest.name());
        appTaskLogPublisher.appendMessage(
                app.getId(),
                task.getId(),
                AppChatMessageRole.TOOL,
                AppChatMessageType.TOOL_CALL,
                content,
                metadata
        );
    }

    private void publishToolResult(
            App app,
            AppTask task,
            ToolExecutionRequest toolRequest,
            WorkspaceToolResult toolResult
    ) {
        Map<String, Object> metadata = Map.of(
                "toolName", toolRequest.name(),
                "isError", toolResult.isError(),
                "isFinished", toolResult.isFinished()
        );
        boolean shouldPersistToolResult = switch (toolRequest.name()) {
            case "writeFile", "deleteFile", "checkProject", "finish" -> true;
            default -> false;
        };
        if (shouldPersistToolResult) {
            appTaskLogPublisher.appendMessage(
                    app.getId(),
                    task.getId(),
                    AppChatMessageRole.TOOL,
                    AppChatMessageType.TOOL_RESULT,
                    preview(toolResult.getContent()),
                    metadata
            );
            return;
        }
        appTaskLogPublisher.publishTransientMessage(
                app.getId(),
                task.getId(),
                AppChatMessageRole.TOOL,
                AppChatMessageType.TOOL_RESULT,
                preview(toolResult.getContent()),
                metadata,
                "agent-trace"
        );
    }

    private String initialUserMessage(App app, AppTask task, WorkspaceToolExecutor tools) {
        String files;
        try {
            files = tools.describeCurrentFiles();
        } catch (IOException e) {
            files = "无法读取当前文件树：" + e.getMessage();
        }

        return """
                应用名称：%s

                用户需求：
                %s

                当前项目文件：
                %s
                """.formatted(app.getName(), task.getPrompt(), files);
    }

    private String iterationUserMessage(App app, AppTask task, WorkspaceToolExecutor tools) {
        String files;
        try {
            files = tools.describeCurrentFiles();
        } catch (IOException e) {
            files = "无法读取当前文件树：" + e.getMessage();
        }

        return """
                应用名称：%s

                这是用户和 Agent 的后续对话迭代需求，请基于已有应用继续修改，不要从零重建项目。

                本轮用户输入：
                %s

                迭代要求：
                - 保留用户未要求移除的已有页面、交互和视觉风格。
                - 优先读取相关文件，理解当前实现后再修改。
                - 只围绕本轮用户输入改动必要文件。
                - 修改完成后必须调用 checkProject，并在 lint/build 全部通过后调用 finish 工具总结本轮迭代内容。

                当前项目文件：
                %s
                """.formatted(app.getName(), task.getPrompt(), files);
    }

    private String compactUserMessage(App app, AppTask task) {
        return """
                应用名称：%s

                用户需求：
                %s
                """.formatted(app.getName(), task.getPrompt());
    }

    String repairUserMessage(
            App app,
            AppTask task,
            WorkspaceToolExecutor tools,
            ProjectCommandResult failedCommand,
            int repairAttempt
    ) {
        String files;
        try {
            files = tools.describeCurrentFiles();
        } catch (IOException e) {
            files = "无法读取当前文件树：" + e.getMessage();
        }

        return """
                应用名称：%s

                用户原始需求：
                %s

                自动修复轮次：第 %d 轮

                后端验证命令失败，请基于错误信息修复当前工作区代码。

                失败命令：
                %s

                退出码：
                %s

                错误日志：
                %s

                当前项目文件：
                %s

                修复要求：
                - 先读取相关文件，定位 lint 或 build 失败原因。
                - 只修改业务实现需要的文件，不要绕过校验。
                - 不要关闭 ESLint 规则，不要删除校验命令，不要修改 package.json、pnpm-lock.yaml、eslint.config.js 等受保护底座文件。
                - 修复完成后必须调用 checkProject，并在 lint/build 全部通过后调用 finish 工具简要说明修复内容。
                """.formatted(
                app.getName(),
                task.getPrompt(),
                repairAttempt,
                failedCommand.getCommandText(),
                failedCommand.getExitCode() == null ? "未知" : failedCommand.getExitCode(),
                failedCommand.getContent(),
                files
        );
    }

    private String summarizeToolCall(ToolExecutionRequest request) {
        String arguments = request.arguments();
        if (arguments == null || arguments.isBlank()) {
            return "调用工具：" + request.name();
        }
        String path = extractArgument(arguments, "path");
        String query = extractArgument(arguments, "query");
        if (path != null) {
            return "调用工具：" + request.name() + " " + path;
        }
        if (query != null) {
            return "调用工具：" + request.name() + " " + query;
        }
        return "调用工具：" + request.name();
    }

    private String extractArgument(String arguments, String key) {
        try {
            Map<?, ?> map = objectMapper.readValue(arguments, Map.class);
            Object value = map.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (IOException e) {
            return null;
        }
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= LOG_PREVIEW_LIMIT) {
            return content;
        }
        return content.substring(0, LOG_PREVIEW_LIMIT) + "\n...[truncated]";
    }
}
