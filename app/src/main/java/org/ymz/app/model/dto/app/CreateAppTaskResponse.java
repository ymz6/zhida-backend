package org.ymz.app.model.dto.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建应用任务响应。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAppTaskResponse {

    private Long appId;

    private Long taskId;

    private String name;

    private String status;
}
