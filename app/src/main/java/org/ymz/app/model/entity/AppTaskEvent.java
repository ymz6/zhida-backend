package org.ymz.app.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用任务运行事件表 实体类。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_task_event")
public class AppTaskEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long appId;

    private Long taskId;

    /**
     * 事件名称，例如 agent.tool.called。
     */
    private String eventType;

    /**
     * 事件显示内容。
     */
    private String content;

    /**
     * 结构化元数据，JSON 字符串格式。
     */
    private String metadata;

    private LocalDateTime createdAt;
}
