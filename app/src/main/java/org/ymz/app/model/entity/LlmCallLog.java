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
 * 大语言模型调用明细日志表 实体类。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("llm_call_log")
public class LlmCallLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 调用场景
     */
    private String scenario;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型响应 ID
     */
    private String responseId;

    /**
     * 模型停止原因
     */
    private String finishReason;

    /**
     * 应用 ID
     */
    private Long appId;

    /**
     * 任务 ID
     */
    private Long taskId;

    /**
     * 调用状态：SUCCESS-成功，FAILED-失败
     */
    private String status;

    /**
     * 输入 Token 数
     */
    private Long inputTokens;

    /**
     * 输出 Token 数
     */
    private Long outputTokens;

    /**
     * 总 Token 数
     */
    private Long totalTokens;

    /**
     * 调用耗时，单位毫秒
     */
    private Long durationMillis;

    /**
     * 错误类型
     */
    private String errorType;

    /**
     * 调用失败信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
