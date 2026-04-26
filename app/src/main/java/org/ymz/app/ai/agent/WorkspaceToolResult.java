package org.ymz.app.ai.agent;

import lombok.Builder;
import lombok.Data;

/**
 * 工作区工具调用结果。
 *
 * @author ymz
 */
@Data
@Builder
public class WorkspaceToolResult {

    private String content;

    private boolean error;

    private boolean finished;
}
