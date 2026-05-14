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
 * 应用审核记录表实体类。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("audit_record")
public class AuditRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 应用 ID
     */
    private Long appId;

    /**
     * 审核记录状态：1-待审核，2-审核通过，3-审核拒绝，4-用户撤回
     */
    private Integer status;

    /**
     * 审核人 ID
     */
    private Long auditorId;

    /**
     * 审核意见
     */
    private String remark;

    /**
     * 审核或撤回完成时间
     */
    private LocalDateTime auditTime;

    /**
     * 创建时间，即用户提审时间
     */
    private LocalDateTime createdAt;
}
