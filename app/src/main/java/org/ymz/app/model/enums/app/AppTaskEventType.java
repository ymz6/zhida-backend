package org.ymz.app.model.enums.app;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 任务运行事件类型。
 *
 * @author ymz
 */
@Getter
@RequiredArgsConstructor
public enum AppTaskEventType {

    ASSISTANT_COMPLETED("assistant.completed"),
    AGENT_RUN_STARTED("agent.run.started"),
    AGENT_STAGE_CHANGED("agent.stage.changed"),
    AGENT_COMMAND_STARTED("agent.command.started"),
    AGENT_COMMAND_SUCCEEDED("agent.command.succeeded"),
    AGENT_COMMAND_FAILED("agent.command.failed"),
    AGENT_TOOL_CALLED("agent.tool.called"),
    AGENT_TOOL_SUCCEEDED("agent.tool.succeeded"),
    AGENT_TOOL_FAILED("agent.tool.failed"),
    AGENT_VALIDATION_STARTED("agent.validation.started"),
    AGENT_VALIDATION_FAILED("agent.validation.failed"),
    AGENT_REPAIR_STARTED("agent.repair.started"),
    AGENT_RUN_FINISHED("agent.run.finished"),
    AGENT_RUN_FAILED("agent.run.failed");

    private final String code;

    public static AppTaskEventType fromCode(String code) {
        for (AppTaskEventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知任务事件类型: " + code);
    }
}
