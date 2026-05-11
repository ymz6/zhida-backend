package org.ymz.app.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大语言模型日志表 实体类。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("llm_log")
public class LlmLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 用户 ID
     */
    private Long userId;

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
     * 模型返回的原始 usage JSON
     */
    private String usageJson;

    /**
     * 调用耗时，单位毫秒
     */
    private Long durationMillis;

    /**
     * 调用失败信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
