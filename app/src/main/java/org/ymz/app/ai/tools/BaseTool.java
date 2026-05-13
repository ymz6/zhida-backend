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
        return "\n\n【选择工具】%s\n".formatted(displayName());
    }

    /**
     * 格式化工具成功调用后的响应内容
     * 
     * @param arguments 请求时的参数
     * @param result    工具调用结果
     */
    default String formatResponse(JSONObject arguments, String result) {
        return "\n【工具调用结果】%s\n\n".formatted(result);
    }
}
