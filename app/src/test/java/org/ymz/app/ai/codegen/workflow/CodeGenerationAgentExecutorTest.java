package org.ymz.app.ai.codegen.workflow;

import org.ymz.app.ai.codegen.event.CodeGenerationTaskEventRecorder;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.ymz.app.ai.codegen.runtime.CodeGenerationInvocationGuard;
import org.ymz.app.ai.codegen.memory.CodeGenerationRecoveryContextService;
import org.ymz.app.ai.codegen.agent.CreateCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.IterateCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.RepairCodeGenerationAiService;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeGenerationAgentExecutorTest {

    @TempDir
    Path workspacePath;

    @Test
    void flushesAssistantMessageBeforeToolCall() {
        CodeGenerationTaskEventRecorder publisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationAgentExecutor facade = facade(publisher, scriptedStream(List.of(
                step -> step.partial("先分析项目结构。"),
                step -> step.beforeTool(toolRequest("readFile")))));

        facade.generate(app(), task(), workspacePath);

        InOrder inOrder = inOrder(publisher);
        inOrder.verify(publisher).appendAssistantMessage(any(), eq("先分析项目结构。"));
        inOrder.verify(publisher).publishToolCalled(any(), any());
    }

    @Test
    void splitsAssistantMessagesAcrossMultipleToolBoundaries() {
        CodeGenerationTaskEventRecorder publisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationAgentExecutor facade = facade(publisher, scriptedStream(List.of(
                step -> step.partial("第一段说明。"),
                step -> step.beforeTool(toolRequest("readFile")),
                step -> step.partial("第二段说明。"),
                step -> step.beforeTool(toolRequest("writeFile")),
                step -> step.partial("最终总结。"))));

        facade.generate(app(), task(), workspacePath);

        verify(publisher, times(3)).appendAssistantMessage(any(), any());
        verify(publisher).appendAssistantMessage(any(), eq("第一段说明。"));
        verify(publisher).appendAssistantMessage(any(), eq("第二段说明。"));
        verify(publisher).appendAssistantMessage(any(), eq("最终总结。"));
    }

    @Test
    void doesNotAppendDuplicateFinalSummaryWhenAlreadyFlushed() {
        CodeGenerationTaskEventRecorder publisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationAgentExecutor facade = facade(publisher, scriptedStream(List.of(
                step -> step.partial("任务已完成。"),
                step -> step.beforeTool(toolRequest("finish")),
                step -> step.partial("任务已完成。"))));

        AtomicReference<String> last = new AtomicReference<>("任务已完成。");
        assertFalse(facade.shouldAppendFinalSummary("任务已完成。", last.get()));
    }

    @Test
    void appendFinalSummaryWhenNoPreviousAssistantMessage() {
        CodeGenerationTaskEventRecorder publisher = mock(CodeGenerationTaskEventRecorder.class);
        CodeGenerationAgentExecutor facade = facade(publisher, scriptedStream(List.of()));

        assertTrue(facade.shouldAppendFinalSummary("任务已完成。", null));
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
        when(createService.generate(any(), any(), any())).thenReturn(tokenStream);
        CodeGenerationRecoveryContextService recovery = mock(CodeGenerationRecoveryContextService.class);
        return new CodeGenerationAgentExecutor(
                createService,
                iterateService,
                repairService,
                publisher,
                new CodeGenerationInvocationGuard(),
                recovery);
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

    private ToolExecutionRequest toolRequest(String name) {
        return ToolExecutionRequest.builder()
                .name(name)
                .arguments("{}")
                .build();
    }

    private TokenStream scriptedStream(List<Consumer<ScriptedTokenStream>> script) {
        return new ScriptedTokenStream(script);
    }

    private static class ScriptedTokenStream implements TokenStream {

        private final List<Consumer<ScriptedTokenStream>> script;
        private final List<Consumer<String>> partialHandlers = new ArrayList<>();
        private final List<Consumer<BeforeToolExecution>> beforeToolHandlers = new ArrayList<>();
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
        public TokenStream onToolExecuted(Consumer<dev.langchain4j.service.tool.ToolExecution> toolExecuteHandler) {
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
    }
}
