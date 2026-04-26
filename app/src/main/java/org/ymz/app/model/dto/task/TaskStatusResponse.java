package org.ymz.app.model.dto.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ymz.app.model.entity.AppTask;

/**
 * 任务当前状态响应。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusResponse {

    private Long taskId;

    private Long appId;

    private String status;

    private String currentStep;

    public static TaskStatusResponse of(AppTask task) {
        return TaskStatusResponse.builder()
                .taskId(task.getId())
                .appId(task.getAppId())
                .status(task.getStatus())
                .currentStep(task.getCurrentStep())
                .build();
    }
}
