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
 * 应用案例表 实体类。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_case")
public class AppCase implements Serializable {

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
     * 投稿用户 ID
     */
    private Long userId;

    /**
     * 案例标题
     */
    private String title;

    /**
     * 案例简介
     */
    private String summary;

    /**
     * 状态：PENDING-待审核，APPROVED-已公开，REJECTED-已驳回，OFFLINE-已下架
     */
    private String status;

    /**
     * 是否精选
     */
    private Boolean featured;

    /**
     * 审核通过时的应用名称快照
     */
    private String snapshotAppName;

    /**
     * 审核通过时的应用访问地址快照
     */
    private String snapshotDeployUrl;

    /**
     * 审核通过时的应用封面地址快照
     */
    private String snapshotCoverUrl;

    /**
     * 审核管理员 ID
     */
    private Long reviewerId;

    /**
     * 审核备注
     */
    private String reviewRemark;

    /**
     * 最近审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
