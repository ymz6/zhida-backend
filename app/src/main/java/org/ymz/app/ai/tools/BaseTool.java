package org.ymz.app.ai.tools;

import cn.hutool.json.JSONObject;

/**
 * 工具基类
 * 定义所有工具的通用接口
 *
 * @author ymz
 */
public interface BaseTool {

    /**
     * 工具标识符，对应工具方法名
     */
    String toolName();

    /**
     * 工具显示名称，用于前端展示
     */
    String displayName();

    /**
     * 格式化工具请求内容
     * 
     * @param arguments 请求参数
     */
    default String formatRequest(JSONObject arguments) {
        return toolCallTag(toolName(), displayName(), displayName());
    }

    /**
     * 格式化工具成功调用后的响应内容
     * 
     * @param arguments 请求时的参数
     * @param result    工具调用结果
     */
    default String formatResponse(JSONObject arguments, String result) {
        return toolResultTag(toolName(), displayName(), true, result);
    }

    /**
     * 将工具调用包装为可持久化的聊天正文标签，保证实时流和历史回放使用同一份内容。
     */
    static String toolCallTag(String toolName, String title, String content) {
        return """
                \n<zhida-tool-call name="%s" title="%s">
                %s
                </zhida-tool-call>
                """.formatted(escapeAttribute(toolName), escapeAttribute(title), content);
    }

    /**
     * 将工具结果包装为可持久化的聊天正文标签。
     */
    static String toolResultTag(String toolName, String title, boolean success, String content) {
        return """
                \n<zhida-tool-result name="%s" title="%s" success="%s">
                %s
                </zhida-tool-result>
                """.formatted(escapeAttribute(toolName), escapeAttribute(title), success, content);
    }

    static String escapeAttribute(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
