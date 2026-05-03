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
 * 大语言模型调用明细日志。
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

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String scenario;

    private String modelName;

    private Long appId;

    private Long taskId;

    private String status;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

    private Long durationMillis;

    private String errorMessage;

    private LocalDateTime createdAt;
}
