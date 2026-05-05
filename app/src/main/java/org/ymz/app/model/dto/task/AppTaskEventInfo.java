package org.ymz.app.model.dto.task;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务运行事件信息。
 *
 * @author ymz
 */
@Data
public class AppTaskEventInfo {

    private Long id;

    private Long appId;

    private Long taskId;

    private String eventType;

    private String content;

    private String metadata;

    private LocalDateTime createdAt;
}
