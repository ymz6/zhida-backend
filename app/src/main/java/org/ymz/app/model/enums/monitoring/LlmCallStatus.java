package org.ymz.app.model.enums.monitoring;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * LLM 调用日志状态，仅用于 llm_call_log.status 字段。
 *
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum LlmCallStatus {

    SUCCESS("调用成功"),
    FAILED("调用失败");

    private final String description;
}
