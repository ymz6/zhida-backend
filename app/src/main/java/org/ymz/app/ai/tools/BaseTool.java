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
     */
    default String formatRequest(JSONObject arguments) {
        return String.format("\n\n[选择工具] %s\n\n", displayName());
    }

    /**
     * 格式化工具响应内容
     */
    String formatResponse(JSONObject arguments);
}
