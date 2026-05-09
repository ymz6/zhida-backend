package org.ymz.app.ai.codegen.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.ymz.app.ai.codegen.agent.ChatCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.CreateCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.IterateCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.RepairCodeGenerationAiService;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskEventRecorder;
import org.ymz.app.ai.codegen.memory.CodeGenerationRecoveryContextService;
import org.ymz.app.ai.codegen.runtime.CodeGenerationInvocationGuard;
import org.ymz.app.model.dto.app.content.ContentBlock;
import org.ymz.app.model.dto.app.content.TextBlock;
import org.ymz.app.model.dto.app.content.ToolUseBlock;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CodeGenerationAgentExecutorTest {

    @TempDir
    Path workspacePath;

    @Test
    void appendsOneAssistantMessageWithTextAndToolBlocks() {
        CodeGenerationTaskEventRecorder publisher = mock(CodeGenerationTaskEventRecorder.class);
        ToolExecutionRequest request = toolRequest("read_file", "{\"path\":\"src/App.jsx\"}");
        CodeGenerationAgentExecutor facade = facade(publisher, scriptedStream(List.of(
                step -> step.partial("先分析项目结构。"),
                step -> step.beforeTool(request),
                step -> step.toolExecuted(toolExecution(request, "读取成功", false)),
                step -> step.partial("最终总结。"))));

        facade.generate(app(), task(), workspacePath);

        ArgumentCaptor<List<ContentBlock>> blocksCaptor = ArgumentCaptor.forClass(List.class);
        verify(publisher, times(1)).appendAssistantMessage(any(), blocksCaptor.capture());
        List<ContentBlock> blocks = blocksCaptor.getValue();

        assertEquals(3, blocks.size());
        TextBlock firstText = assertInstanceOf(TextBlock.class, blocks.get(0));
        assertEquals("先分析项目结构。", firstText.text());
        ToolUseBlock toolUse = assertInstanceOf(ToolUseBlock.class, blocks.get(1));
        assertEquals("read_file", toolUse.name());
        assertEquals(Map.of("path", "src/App.jsx"), toolUse.input());
        assertEquals("读取成功", toolUse.result());
        TextBlock lastText = assertInstanceOf(TextBlock.class, blocks.get(2));
        assertEquals("最终总结。", lastText.text());

        InOrder inOrder = inOrder(publisher);
        inOrder.verify(publisher).publishToolCalled(any(), any());
        inOrder.verify(publisher).publishToolExecuted(any(), any());
        inOrder.verify(publisher).appendAssistantMessage(any(), any());
    }

    @Test
    void appendsSinglePureTextAssistantMessage() {
        CodeGenerationTaskEventRecorder publisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationAgentExecutor facade = facade(publisher, scriptedStream(List.of(
                step -> step.partial("任务已完成。"))));

        facade.generate(app(), task(), workspacePath);

        ArgumentCaptor<List<ContentBlock>> blocksCaptor = ArgumentCaptor.forClass(List.class);
        verify(publisher, times(1)).appendAssistantMessage(any(), blocksCaptor.capture());
        TextBlock textBlock = assertInstanceOf(TextBlock.class, blocksCaptor.getValue().getFirst());
        assertEquals("任务已完成。", textBlock.text());
    }

    @Test
    void truncatesSuccessfulToolResultAndDropsFailedResult() {
        CodeGenerationTaskEventRecorder publisher = mock(CodeGenerationTaskEventRecorder.class);
        ToolExecutionRequest successRequest = toolRequest("write_file", "{\"path\":\"src/App.jsx\"}");
        ToolExecutionRequest failedRequest = toolRequest("run_command", "{}");
        String longResult = "a".repeat(2_050);
        CodeGenerationAgentExecutor facade = facade(publisher, scriptedStream(List.of(
                step -> step.beforeTool(successRequest),
                step -> step.toolExecuted(toolExecution(successRequest, longResult, false)),
                step -> step.beforeTool(failedRequest),
                step -> step.toolExecuted(toolExecution(failedRequest, "失败详情", true)))));

        facade.generate(app(), task(), workspacePath);

        ArgumentCaptor<List<ContentBlock>> blocksCaptor = ArgumentCaptor.forClass(List.class);
        verify(publisher).appendAssistantMessage(any(), blocksCaptor.capture());
        ToolUseBlock successBlock = assertInstanceOf(ToolUseBlock.class, blocksCaptor.getValue().get(0));
        ToolUseBlock failedBlock = assertInstanceOf(ToolUseBlock.class, blocksCaptor.getValue().get(1));
        assertEquals(2_015, successBlock.result().length());
        assertEquals("\n...[truncated]", successBlock.result().substring(2_000));
        assertNull(failedBlock.result());
    }

    @Test
    void publishesDeltaButNoAssistantMessageWhenNoTextProduced() {
        CodeGenerationTaskEventRecorder publisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationAgentExecutor facade = facade(publisher, scriptedStream(List.of()));

        facade.generate(app(), task(), workspacePath);

        verify(publisher, never()).appendAssistantMessage(any(), any());
    }

    private CodeGenerationAgentExecutor facade(CodeGenerationTaskEventRecorder publisher, TokenStream tokenStream) {
        CreateCodeGenerationAiService createService = mock(CreateCodeGenerationAiService.class);
        IterateCodeGenerationAiService iterateService = mock(IterateCodeGenerationAiService.class);
        RepairCodeGenerationAiService repairService = mock(RepairCodeGenerationAiService.class);
        ChatCodeGenerationAiService chatService = mock(ChatCodeGenerationAiService.class);
        when(createService.generate(any(), any(), any())).thenReturn(tokenStream);
        CodeGenerationRecoveryContextService recovery = mock(CodeGenerationRecoveryContextService.class);
        return new CodeGenerationAgentExecutor(
                createService,
                iterateService,
                repairService,
                chatService,
                publisher,
                new CodeGenerationInvocationGuard(),
                recovery,
                new ObjectMapper());
    }

    private App app() {
        return App.builder()
                .id(1L)
                .name("测试应用")
                .build();
    }

    private AppTask task() {
        return AppTask.builder()
                .id(2L)
                .appId(1L)
                .prompt("做一个任务看板")
                .build();
    }

    private ToolExecutionRequest toolRequest(String name, String arguments) {
        return ToolExecutionRequest.builder()
                .name(name)
                .arguments(arguments)
                .build();
    }

    private ToolExecution toolExecution(ToolExecutionRequest request, String result, boolean failed) {
        return ToolExecution.builder()
                .request(request)
                .result(ToolExecutionResult.builder()
                        .resultText(result)
                        .isError(failed)
                        .build())
                .invocationContext(InvocationContext.builder().build())
                .build();
    }

    private TokenStream scriptedStream(List<Consumer<ScriptedTokenStream>> script) {
        return new ScriptedTokenStream(script);
    }

    private static class ScriptedTokenStream implements TokenStream {

        private final List<Consumer<ScriptedTokenStream>> script;
        private final List<Consumer<String>> partialHandlers = new ArrayList<>();
        private final List<Consumer<BeforeToolExecution>> beforeToolHandlers = new ArrayList<>();
        private final List<Consumer<ToolExecution>> toolExecutedHandlers = new ArrayList<>();
        private Consumer<ChatResponse> completeHandler;
        private Consumer<Throwable> errorHandler;

        private ScriptedTokenStream(List<Consumer<ScriptedTokenStream>> script) {
            this.script = script;
        }

        @Override
        public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
            partialHandlers.add(partialResponseHandler);
            return this;
        }

        @Override
        public TokenStream onRetrieved(Consumer<List<dev.langchain4j.rag.content.Content>> contentHandler) {
            return this;
        }

        @Override
        public TokenStream beforeToolExecution(Consumer<BeforeToolExecution> beforeToolExecutionHandler) {
            beforeToolHandlers.add(beforeToolExecutionHandler);
            return this;
        }

        @Override
        public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecuteHandler) {
            toolExecutedHandlers.add(toolExecuteHandler);
            return this;
        }

        @Override
        public TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler) {
            this.completeHandler = completeResponseHandler;
            return this;
        }

        @Override
        public TokenStream onError(Consumer<Throwable> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        @Override
        public TokenStream ignoreErrors() {
            return this;
        }

        @Override
        public void start() {
            try {
                for (Consumer<ScriptedTokenStream> consumer : script) {
                    consumer.accept(this);
                }
                if (completeHandler != null) {
                    completeHandler.accept(mock(ChatResponse.class));
                }
            } catch (Throwable throwable) {
                if (errorHandler != null) {
                    errorHandler.accept(throwable);
                } else {
                    throw throwable;
                }
            }
        }

        private void partial(String text) {
            for (Consumer<String> handler : partialHandlers) {
                handler.accept(text);
            }
        }

        private void beforeTool(ToolExecutionRequest request) {
            BeforeToolExecution beforeToolExecution = BeforeToolExecution.builder()
                    .request(request)
                    .invocationContext(InvocationContext.builder().build())
                    .build();
            for (Consumer<BeforeToolExecution> handler : beforeToolHandlers) {
                handler.accept(beforeToolExecution);
            }
        }

        private void toolExecuted(ToolExecution toolExecution) {
            for (Consumer<ToolExecution> handler : toolExecutedHandlers) {
                handler.accept(toolExecution);
            }
        }
    }
}
