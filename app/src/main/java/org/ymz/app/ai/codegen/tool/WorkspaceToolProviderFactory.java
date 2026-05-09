package org.ymz.app.ai.codegen.tool;

import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.codegen.runtime.CodeGenerationContext;
import org.ymz.app.ai.codegen.workspace.CodeGenerationProjectVerifier;
import org.ymz.app.model.enums.codegen.CodeGenerationScenario;

import java.util.List;

/**
 * 为单次 AI Service 调用动态构造工作区工具。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class WorkspaceToolProviderFactory {

    private final CodeGenerationProjectVerifier projectVerifier;

    public ToolProvider create() {
        return new ToolProvider() {
            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                CodeGenerationContext context = request.invocationParameters().get("codegenContext");
                if (context == null) {
                    throw new IllegalStateException("缺少 codegenContext");
                }
                WorkspaceToolSession session = new WorkspaceToolSession(
                        context.getWorkspacePath(),
                        projectVerifier,
                        context.getAppId(),
                        context.getTaskId());
                request.invocationParameters().put("workspaceToolSession", session);
                List<Object> tools = context.getScenario() == CodeGenerationScenario.CHAT
                        ? List.of(new FileReadTool(session), new GlobTool(session), new GrepTool(session))
                        : List.of(
                        new FileReadTool(session),
                        new FileWriteTool(session),
                        new FileEditTool(session),
                        new FileDeleteTool(session),
                        new GlobTool(session),
                        new GrepTool(session),
                        new CheckTool(session),
                        new BuildTool(session),
                        new FinishTool(session)
                );
                ToolProviderResult.Builder builder = ToolProviderResult.builder();
                for (Object tool : tools) {
                    // 当前 LangChain4j 版本只支持单对象发现工具，这里逐个合并到动态 Provider。
                    for (AiServiceTool aiServiceTool : ToolService.findTools(tool)) {
                        builder.add(aiServiceTool.toolSpecification(), aiServiceTool.toolExecutor());
                    }
                }
                return builder.build();
            }
        };
    }
}
