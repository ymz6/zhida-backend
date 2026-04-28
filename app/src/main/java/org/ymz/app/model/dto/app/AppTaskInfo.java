package org.ymz.app.model.dto.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用任务信息。
 *
 * @author ymz
 */
@Data
public class AppTaskInfo {

    private Long id;

    private Long appId;

    private String taskType;

    private String prompt;

    private String status;

    private String currentStep;

    private String errorMessage;

    private String resultSummary;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
