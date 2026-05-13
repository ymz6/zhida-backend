package org.ymz.app.ai.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 工具注册表
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AiToolRegistry {

    private final List<BaseTool> tools;

    /**
     * 返回 LangChain4j 可注册的工具对象。
     */
    public Object[] allTools() {
        return tools.toArray();
    }

    /**
     * 根据模型返回的工具名查找展示与格式化信息。
     */
    public BaseTool getByName(String toolName) {
        return tools.stream()
                .filter(tool -> tool.toolName().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知工具: " + toolName));
    }
}
