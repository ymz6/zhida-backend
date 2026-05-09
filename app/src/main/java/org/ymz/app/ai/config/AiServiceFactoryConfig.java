package org.ymz.app.ai.config;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ymz.app.ai.codegen.memory.CodeGenerationChatMemoryFactory;
import org.ymz.app.ai.codegen.memory.AppContextSummaryAssistant;
import org.ymz.app.ai.codegen.runtime.CodeGenerationPromptContextComposer;
import org.ymz.app.ai.codegen.tool.WorkspaceToolProviderFactory;
import org.ymz.app.ai.codegen.agent.ChatCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.CreateCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.IterateCodeGenerationAiService;
import org.ymz.app.ai.codegen.agent.RepairCodeGenerationAiService;
import org.ymz.app.ai.title.TitleGenerateAssistant;
import org.ymz.app.monitoring.CodeGenerationAiServiceObservability;

/**
 * AI Service 工厂配置
 * @author ymz
 */
@Configuration
public class AiServiceFactoryConfig {

    /**
     * 标题生成助手
     */
    @Bean
    TitleGenerateAssistant titleGenerateAssistant(ChatModel titleGenerateModel) {
        return AiServices.builder(TitleGenerateAssistant.class)
                .chatModel(titleGenerateModel)
                .build();
    }

    @Bean
    AppContextSummaryAssistant appContextSummaryAssistant(ChatModel codeSummaryModel) {
        return AiServices.builder(AppContextSummaryAssistant.class)
                .chatModel(codeSummaryModel)
                .build();
    }

    @Bean
    CreateCodeGenerationAiService createCodeGenerationAiService(
            StreamingChatModel codeGenerateModel,
            CodeGenerationChatMemoryFactory memoryFactory,
            WorkspaceToolProviderFactory toolProviderFactory,
            CodeGenerationPromptContextComposer promptContextComposer,
            CodeGenerationAiServiceObservability observability
    ) {
        return buildCodeGenerationAiService(
                CreateCodeGenerationAiService.class,
                codeGenerateModel,
                memoryFactory,
                toolProviderFactory,
                promptContextComposer,
                observability
        );
    }

    @Bean
    IterateCodeGenerationAiService iterateCodeGenerationAiService(
            StreamingChatModel codeGenerateModel,
            CodeGenerationChatMemoryFactory memoryFactory,
            WorkspaceToolProviderFactory toolProviderFactory,
            CodeGenerationPromptContextComposer promptContextComposer,
            CodeGenerationAiServiceObservability observability
    ) {
        return buildCodeGenerationAiService(
                IterateCodeGenerationAiService.class,
                codeGenerateModel,
                memoryFactory,
                toolProviderFactory,
                promptContextComposer,
                observability
        );
    }

    @Bean
    RepairCodeGenerationAiService repairCodeGenerationAiService(
            StreamingChatModel codeGenerateModel,
            CodeGenerationChatMemoryFactory memoryFactory,
            WorkspaceToolProviderFactory toolProviderFactory,
            CodeGenerationPromptContextComposer promptContextComposer,
            CodeGenerationAiServiceObservability observability
    ) {
        return buildCodeGenerationAiService(
                RepairCodeGenerationAiService.class,
                codeGenerateModel,
                memoryFactory,
                toolProviderFactory,
                promptContextComposer,
                observability
        );
    }

    @Bean
    ChatCodeGenerationAiService chatCodeGenerationAiService(
            StreamingChatModel codeGenerateModel,
            CodeGenerationChatMemoryFactory memoryFactory,
            WorkspaceToolProviderFactory toolProviderFactory,
            CodeGenerationPromptContextComposer promptContextComposer,
            CodeGenerationAiServiceObservability observability
    ) {
        return buildCodeGenerationAiService(
                ChatCodeGenerationAiService.class,
                codeGenerateModel,
                memoryFactory,
                toolProviderFactory,
                promptContextComposer,
                observability
        );
    }

    private <T> T buildCodeGenerationAiService(
            Class<T> type,
            StreamingChatModel codeGenerateModel,
            CodeGenerationChatMemoryFactory memoryFactory,
            WorkspaceToolProviderFactory toolProviderFactory,
            CodeGenerationPromptContextComposer promptContextComposer,
            CodeGenerationAiServiceObservability observability
    ) {
        return AiServices.builder(type)
                .streamingChatModel(codeGenerateModel)
                .chatMemoryProvider(memoryFactory.provider())
                .toolProvider(toolProviderFactory.create())
                .hallucinatedToolNameStrategy(AiServiceFactoryConfig::hallucinatedToolNameResult)
                .systemMessageTransformer(promptContextComposer::compose)
                .registerListener(observability.requestIssuedListener())
                .registerListener(observability.responseReceivedListener())
                .registerListener(observability.errorListener())
                .build();
    }

    private static ToolExecutionResultMessage hallucinatedToolNameResult(ToolExecutionRequest request) {
        return ToolExecutionResultMessage.builder()
                .id(request.id())
                .toolName(request.name())
                .text("""
                        工具 "%s" 不存在，不能继续使用该工具名。请改用以下可用工具之一：
                        readFile、writeFile、editFile、deleteFile、glob、grep、check、build、finish。

                        如果你想做局部文本替换，请先 readFile 获取准确原文片段，再调用 editFile。
                        """.formatted(request.name()))
                .isError(true)
                .build();
    }
}
