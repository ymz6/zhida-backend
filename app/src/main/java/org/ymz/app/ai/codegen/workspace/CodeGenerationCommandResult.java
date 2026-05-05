package org.ymz.app.ai.codegen.workspace;

import lombok.Builder;
import lombok.Data;

/**
 * 项目命令执行结果。
 *
 * @author ymz
 */
@Data
@Builder
public class CodeGenerationCommandResult {

    private String[] command;

    private String commandText;

    private String content;

    private Integer exitCode;

    private boolean success;

    private boolean timedOut;

    private long durationMillis;

    private String errorMessage;
}
