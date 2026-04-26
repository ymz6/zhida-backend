package org.ymz.app.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;
import org.ymz.app.service.generation.AppTaskLogPublisher;
import org.ymz.app.service.generation.ProjectCommandResult;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class CodeGenerationAgentTest {

    @TempDir
    Path workspacePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void repairUserMessageContainsFailedCommandLogAndRepairAttempt() throws Exception {
        Files.createDirectories(workspacePath.resolve("src/pages"));
        Files.writeString(workspacePath.resolve("src/pages/IndexPage.jsx"), "export default function IndexPage() {}");

        CodeGenerationAgent agent = new CodeGenerationAgent(null, objectMapper, null, null);
        WorkspaceToolExecutor tools = new WorkspaceToolExecutor(workspacePath, objectMapper);
        App app = App.builder()
                .id(1L)
                .name("测试应用")
                .build();
        AppTask task = AppTask.builder()
                .id(2L)
                .prompt("做一个任务看板")
                .build();
        ProjectCommandResult failedCommand = ProjectCommandResult.builder()
                .command(new String[]{"pnpm.cmd", "lint"})
                .commandText("pnpm.cmd lint")
                .content("$ pnpm.cmd lint\nsrc/pages/IndexPage.jsx\n  1:1  error  demo lint error")
                .exitCode(1)
                .success(false)
                .build();

        String message = agent.repairUserMessage(app, task, tools, failedCommand, 2);

        assertTrue(message.contains("自动修复轮次：第 2 轮"));
        assertTrue(message.contains("pnpm.cmd lint"));
        assertTrue(message.contains("demo lint error"));
        assertTrue(message.contains("做一个任务看板"));
        assertTrue(message.contains("src/pages/IndexPage.jsx"));
        assertTrue(message.contains("不要关闭 ESLint 规则"));
        assertTrue(message.contains("checkProject"));
    }

    @Test
    void systemPromptContainsComponentSplitAndCheckProjectRules() {
        CodeGenerationAgent agent = new CodeGenerationAgent(null, objectMapper, null, null);

        String prompt = agent.loadSystemPrompt();

        assertTrue(prompt.contains("适度拆分业务组件"));
        assertTrue(prompt.contains("复杂页面先拆组件再组装"));
        assertTrue(prompt.contains("checkProject"));
        assertTrue(prompt.contains("pnpm lint"));
        assertTrue(prompt.contains("pnpm build"));
    }

    @Test
    void assistantMessagesArePersisted() throws Exception {
        AppTaskLogPublisher publisher = mock(AppTaskLogPublisher.class);
        CodeGenerationAgent agent = new CodeGenerationAgent(null, objectMapper, publisher, null);

        invokePublishAssistantMessage(agent, app(), task(), "已完成页面规划");

        verify(publisher).appendMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.ASSISTANT),
                eq(AppChatMessageType.CHAT),
                eq("已完成页面规划")
        );
        verify(publisher, never()).publishTransientMessage(
                anyLong(),
                anyLong(),
                any(AppChatMessageRole.class),
                any(AppChatMessageType.class),
                anyString(),
                anyMap(),
                anyString()
        );
    }

    @Test
    void toolCallsArePersistedAsSummaries() throws Exception {
        AppTaskLogPublisher publisher = mock(AppTaskLogPublisher.class);
        CodeGenerationAgent agent = new CodeGenerationAgent(null, objectMapper, publisher, null);

        invokePublishToolCall(
                agent,
                app(),
                task(),
                request("readFile", "{\"path\":\"src/pages/IndexPage.jsx\"}")
        );

        verify(publisher).appendMessage(
                eq(1L),
                eq(2L),
                eq(AppChatMessageRole.TOOL),
                eq(AppChatMessageType.TOOL_CALL),
                eq("调用工具：readFile src/pages/IndexPage.jsx"),
                eq(Map.of("toolName", "readFile"))
        );
    }

    @Test
    void keyToolResultsArePersisted() throws Exception {
        AppTaskLogPublisher publisher = mock(AppTaskLogPublisher.class);
        CodeGenerationAgent agent = new CodeGenerationAgent(null, objectMapper, publisher, null);

        for (String toolName : List.of("writeFile", "deleteFile", "checkProject", "finish")) {
            boolean finished = "finish".equals(toolName);
            invokePublishToolResult(agent, app(), task(), request(toolName), result(toolName + " result", false, finished));

            verify(publisher).appendMessage(
                    eq(1L),
                    eq(2L),
                    eq(AppChatMessageRole.TOOL),
                    eq(AppChatMessageType.TOOL_RESULT),
                    eq(toolName + " result"),
                    eq(Map.of(
                            "toolName", toolName,
                            "isError", false,
                            "isFinished", finished
                    ))
            );
            verify(publisher, never()).publishTransientMessage(
                    anyLong(),
                    anyLong(),
                    any(AppChatMessageRole.class),
                    any(AppChatMessageType.class),
                    anyString(),
                    anyMap(),
                    anyString()
            );
            reset(publisher);
        }
    }

    @Test
    void largeToolResultsAreOnlyPublishedTransiently() throws Exception {
        AppTaskLogPublisher publisher = mock(AppTaskLogPublisher.class);
        CodeGenerationAgent agent = new CodeGenerationAgent(null, objectMapper, publisher, null);

        for (String toolName : List.of("readFile", "listFiles", "searchFiles")) {
            invokePublishToolResult(agent, app(), task(), request(toolName), result(toolName + " result", false, false));

            verify(publisher).publishTransientMessage(
                    eq(1L),
                    eq(2L),
                    eq(AppChatMessageRole.TOOL),
                    eq(AppChatMessageType.TOOL_RESULT),
                    eq(toolName + " result"),
                    eq(Map.of(
                            "toolName", toolName,
                            "isError", false,
                            "isFinished", false
                    )),
                    eq("agent-trace")
            );
            verify(publisher, never()).appendMessage(
                    anyLong(),
                    anyLong(),
                    any(AppChatMessageRole.class),
                    any(AppChatMessageType.class),
                    anyString(),
                    anyMap()
            );
            reset(publisher);
        }
    }

    private void invokePublishAssistantMessage(CodeGenerationAgent agent, App app, AppTask task, String content)
            throws Exception {
        Method method = CodeGenerationAgent.class.getDeclaredMethod(
                "publishAssistantMessage",
                App.class,
                AppTask.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(agent, app, task, content);
    }

    private void invokePublishToolCall(
            CodeGenerationAgent agent,
            App app,
            AppTask task,
            ToolExecutionRequest request
    ) throws Exception {
        Method method = CodeGenerationAgent.class.getDeclaredMethod(
                "publishToolCall",
                App.class,
                AppTask.class,
                ToolExecutionRequest.class
        );
        method.setAccessible(true);
        method.invoke(agent, app, task, request);
    }

    private void invokePublishToolResult(
            CodeGenerationAgent agent,
            App app,
            AppTask task,
            ToolExecutionRequest request,
            WorkspaceToolResult result
    ) throws Exception {
        Method method = CodeGenerationAgent.class.getDeclaredMethod(
                "publishToolResult",
                App.class,
                AppTask.class,
                ToolExecutionRequest.class,
                WorkspaceToolResult.class
        );
        method.setAccessible(true);
        method.invoke(agent, app, task, request, result);
    }

    private App app() {
        return App.builder()
                .id(1L)
                .build();
    }

    private AppTask task() {
        return AppTask.builder()
                .id(2L)
                .build();
    }

    private ToolExecutionRequest request(String name) {
        return request(name, "{}");
    }

    private ToolExecutionRequest request(String name, String arguments) {
        return ToolExecutionRequest.builder()
                .name(name)
                .arguments(arguments)
                .build();
    }

    private WorkspaceToolResult result(String content, boolean error, boolean finished) {
        return WorkspaceToolResult.builder()
                .content(content)
                .error(error)
                .finished(finished)
                .build();
    }
}
